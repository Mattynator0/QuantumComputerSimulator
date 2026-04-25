package org.example.simulator.algorithm;

import org.example.math.Complex;
import org.example.math.MathUtils;
import org.example.simulator.QuantumCircuit;
import org.example.simulator.register.QuantumRegister;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.example.math.MathUtils.normalizeState;

public class MottonenStateInitialization {

    // adapted code from PennyLane's implementation:
    // https://docs.pennylane.ai/en/stable/_modules/pennylane/templates/state_preparations/mottonen.html#MottonenStatePreparation
    // which itself is based on:
    // Möttönen et al. (2004) <https://arxiv.org/abs/quant-ph/0407010>

    public static void perform(QuantumCircuit qc, QuantumRegister reg, Complex[] state) {
        int n = MathUtils.ceilLog2(state.length);
        assert reg.getQubitCount() == n;

        normalizeState(state);

        double[] magnitudes = new double[state.length];
        for (int i = 0; i < state.length; i++) magnitudes[i] = state[i].absSquared().doubleValue();

        // RY rotations and CNOTs to rotate into correct amplitudes
        for (int k = n; k > 0; k--) {
            double[] alphaYK = alphaY(magnitudes, n, k);
            int[] controls = IntStream.range(k, n).toArray();
            int target = k - 1;
            mottonenApplyUniformRotation(qc, reg, 'y', alphaYK, controls, target);
        }

        double[] omegas = new double[state.length];
        for (int i = 0; i < state.length; i++) omegas[i] = state[i].directionRadians().doubleValue();

        // RZ rotations and CNOTs to rotate into correct relative phases
        for (int k = n; k > 0; k--) {
            double[] alphaZK = alphaZ(omegas, n, k);
            int[] controls = IntStream.range(k, n).toArray();
            int target = k - 1;
            mottonenApplyUniformRotation(qc, reg, 'z', alphaZK, controls, target);
        }

        // keep track of the global phase so the result of running the circuit matches the input state
        double globalPhase = 0.0;
        for (double w : omegas) {
            globalPhase -= w;
        }
        globalPhase /= omegas.length;
        qc.setGlobalPhase(qc.getGlobalPhase() + globalPhase);
    }

    private static double[] alphaZ(double[] omegas, int n, int k) {
        double[] alphas = new double[1 << (n - k)];

        for (int j = 0; j < alphas.length; j++) {
            double numerator = 0;
            for (int l = 0; l < (1 << (k - 1)); l++) {
                int firstIndex = (2 * j + 1) * (1 << (k - 1)) + l;
                int secondIndex = (2 * j) * (1 << (k - 1)) + l;

                numerator += (omegas[firstIndex] - omegas[secondIndex]);
            }
            alphas[j] = numerator / (1 << (k - 1));
        }
        return alphas;
    }

    private static double[] alphaY(double[] magnitudes, int n, int k) {

        double[] alphas = new double[1 << (n - k)];

        for (int j = 0; j < alphas.length; j++) {
            double numerator = 0;
            double denominator = 0;
            for (int l = 0; l < (1 << (k - 1)); l++) {
                int index = (2 * j + 1) * (1 << (k - 1)) + l;
                numerator += magnitudes[index];
            }

            for (int l = 0; l < (1 << k); l++) {
                int index = j * (1 << k) + l;
                denominator += magnitudes[index];
            }

            alphas[j] = denominator == 0
                    ? 0
                    : 2 * Math.asin(Math.sqrt(numerator / denominator));
        }

        return alphas;
    }

    private static void mottonenApplyUniformRotation(QuantumCircuit qc, QuantumRegister reg, char a, double[] alphas, int[] controls, int target) {

        int m = controls.length;
        double[] thetas = computeTheta(alphas);

        if (m == 0) {
            if (Math.abs(thetas[0]) > 1e-12) {
                if (a == 'y') {
                    qc.ry(thetas[0], reg.get(target));
                } else if (a == 'z') {
                    qc.rz(thetas[0], reg.get(target));
                }
            }
            return;
        }

        int[] gray = MathUtils.grayCode(m);

        int[] controlIndices = new int[gray.length];
        for (int i = 0; i < gray.length; i++) {
            int next = gray[(i + 1) % gray.length];
            int diff = gray[i] ^ next;
            if (diff == 0)
                controlIndices[i] = 0;
            else
                controlIndices[i] = Integer.numberOfTrailingZeros(diff);
        }

        for (int i = 0; i < thetas.length; i++) {
            if (Math.abs(thetas[i]) > 1e-12) {
                if (a == 'y') {
                    qc.ry(thetas[i], reg.get(target));
                } else if (a == 'z') {
                    qc.rz(thetas[i], reg.get(target));
                }
            }

            qc.cx(reg.get(controls[controlIndices[i]]), reg.get(target));
        }
    }

    private static double[] computeTheta(double[] alphas) {

        int n = alphas.length;
        if (n == 0)
            return alphas.clone();

        double[] thetas = Arrays.copyOf(alphas, alphas.length);

        // Fast Walsh-Hadamard transform
        for (int step = 1; step < n; step *= 2)
            for (int i = 0; i < n; i += 2 * step)
                for (int j = 0; j < step; j++) {
                    double a = thetas[i + j];
                    double b = thetas[i + j + step];
                    thetas[i + j] = (a + b) / 2;
                    thetas[i + j + step] = (a - b) / 2;
                }

        if (n <= 2)
            return thetas;

        // reorder according to gray code
        double[] reordered = new double[thetas.length];
        int[] gray = MathUtils.grayCode(MathUtils.ceilLog2(n));
        for (int i = 0; i < thetas.length; i++) {
            reordered[i] = thetas[gray[i]];
        }

        return reordered;
    }
}
