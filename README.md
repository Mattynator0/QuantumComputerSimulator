# Quantum Computer Simulator by Mattynator

Simulates circuits of up to 20 qubits. 
Feel free to adjust the constant `QuantumCircuit.MAX_QUBITS` but beware that running such a circuit requires an exponential amount of contiguous memory.

Please keep in mind that this is an early Work In Progress version of the simulator and you may encounter bugs or API inconsistencies.

### Currently supported algorithms:
- Quantum Fourier Transform
- Phase and bit oracles
- Deutsch-Jozsa
- Grover
- Quantum Phase Estimation
- Amplitude Estimation
- Polynomial evaluation
- Grover optimizer on a polynomial
- Shor's factorization algorithm
- Möttönen state initialization

Circuit examples are available in the file `CircuitExamples.java`.
