package org.example.math;

import java.math.BigDecimal;
import java.math.MathContext;

public class BigDecimalMathHelper {

    public static final MathContext MC = new MathContext(10);

    public static final MathContext printMC = new MathContext(3);

    public static final BigDecimal EPS = BigDecimal.ONE
            .movePointLeft(MC.getPrecision());

    public static BigDecimal clampToZero(BigDecimal x) {
        return x.abs().compareTo(EPS) < 0 ? BigDecimal.ZERO : x;
    }

    public static BigDecimal zeroIfTiny(BigDecimal value) {
        return value.abs().compareTo(new BigDecimal("1E-" + (MC.getPrecision() - 2))) <= 0
                ? BigDecimal.ZERO
                : value;
    }
}
