# Ensures the shared local infrastructure containers exist and are running (idempotent). ConnectOne containers only:
#   connectone-postgres :5434   (databases: connectone_dev, connectone_test, connectone_notifications, connectone_notifications_test)
#   connectone-redis    :6380   (optional cache)
#   connectone-kafka    :9092   (KRaft single node, heap capped for a 2 GB Docker VM)
# Never touches ai-lab-postgres.
$ErrorActionPreference = 'Stop'
docker info 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop first." }

function Ensure-Container($name, [scriptblock]$create) {
    $state = docker ps -a --filter "name=^/$name$" --format '{{.State}}'
    if (-not $state) { Write-Host "creating $name"; $ErrorActionPreference = 'Continue'; & $create 2>&1 | Out-Null; $ErrorActionPreference = 'Stop'; if ($LASTEXITCODE -ne 0) { throw "could not create $name" } }
    elseif ($state -ne 'running') { Write-Host "starting $name"; docker start $name | Out-Null }
}

Ensure-Container 'connectone-redis' {
    docker run -d --name connectone-redis --restart unless-stopped -p 127.0.0.1:6380:6379 redis:7-alpine redis-server --maxmemory 64mb --maxmemory-policy allkeys-lru
}
Ensure-Container 'connectone-kafka' {
    docker run -d --name connectone-kafka --restart unless-stopped -p 127.0.0.1:9092:9092 `
        -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller `
        -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 `
        -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT `
        -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 `
        -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 `
        -e KAFKA_NUM_PARTITIONS=3 -e KAFKA_HEAP_OPTS="-Xmx320m -Xms320m" apache/kafka:3.9.0
}
if (-not (docker ps --filter "name=^/connectone-postgres$" --format '{{.Names}}')) { throw "connectone-postgres is not running (see docs/LOCAL_SETUP.md section 1)" }

foreach ($d in 'connectone_notifications', 'connectone_notifications_test') {
    docker exec connectone-postgres sh -c "psql -U `"`$POSTGRES_USER`" -d postgres -tc `"select 1 from pg_database where datname='$d'`" | grep -q 1 || psql -U `"`$POSTGRES_USER`" -d postgres -c 'CREATE DATABASE $d'" | Out-Null
}
$ErrorActionPreference = 'Continue'   # the broker prints warnings to stderr while it starts; only the exit code matters
for ($i = 0; $i -lt 30; $i++) {
    docker exec connectone-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "infrastructure ready (postgres, redis, kafka)"; return }
    Start-Sleep 2
}
throw "Kafka did not become ready"
