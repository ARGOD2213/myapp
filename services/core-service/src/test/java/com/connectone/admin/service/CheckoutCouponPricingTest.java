package com.connectone.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Pure unit test: no Spring context, no database. */
class CheckoutCouponPricingTest {

    @Test
    void roundsHalfUpToTwoDecimals() {
        var quote = CheckoutCouponService.price("X", new BigDecimal("15.00"), new BigDecimal("299.00"));
        assertThat(quote.discountAmount()).isEqualByComparingTo("44.85");
        assertThat(quote.payable()).isEqualByComparingTo("254.15");
    }

    @Test
    void neverDiscountsBelowTheMinimumPayableAmount() {
        var quote = CheckoutCouponService.price("X", new BigDecimal("100.00"), new BigDecimal("29.00"));
        assertThat(quote.payable()).isEqualByComparingTo("1.00");
        assertThat(quote.discountAmount()).isEqualByComparingTo("28.00");
    }

    @Test
    void tinyAmountsGetNoDiscount() {
        var quote = CheckoutCouponService.price("X", new BigDecimal("50.00"), new BigDecimal("1.00"));
        assertThat(quote.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.payable()).isEqualByComparingTo("1.00");
    }
}
