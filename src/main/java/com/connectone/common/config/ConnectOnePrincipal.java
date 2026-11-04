package com.connectone.common.config;

import java.util.List;

public record ConnectOnePrincipal(long userId, String email, List<String> roles, List<String> permissions) {
}
