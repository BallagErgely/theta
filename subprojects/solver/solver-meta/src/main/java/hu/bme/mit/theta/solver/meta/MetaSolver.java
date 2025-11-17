/*
 *  Copyright 2025 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.solver.meta;

import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.solver.Solver;
import hu.bme.mit.theta.solver.SolverStatus;
import hu.bme.mit.theta.solver.Stack;
import hu.bme.mit.theta.solver.impl.StackImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;


class MetaSolver implements  Solver {

    private Solver solver;
    private final List<Solver> solvers;
    private int currentSolverIndex = 0;
    private final Stack<Expr<BoolType>> assertions = new StackImpl<>();
    private final List<Integer> pushes = new ArrayList<>();

    MetaSolver(List<Solver> solvers) {
        var names = solvers.stream().map(Object::getClass).map(Class::getSimpleName).toList();
        logging("construct MetaSolver with " + String.join("; ", names));

        this.solvers = solvers;
        solver = solvers.get(currentSolverIndex);
    }

    @Override
    public void add(Expr<BoolType> assertion) {

        logging("adding " + assertion + " to " + solver.getClass().getSimpleName());

        assertions.add(assertion);
        try {
            solver.add(assertion);
        }
        catch (Exception|Error e) {
            logging("error adding " + assertion + " to " + solver.getClass().getSimpleName());
            logging("switching");
            switchSolvers();
        }

    }

    @Override
    public SolverStatus check() {
        try {
            logging("checking with " + solver.getClass().getSimpleName());
            return solver.check();
        }
        catch (Exception|Error e) {
            logging("error checking with " + solver.getClass().getSimpleName() + ": " + e.getMessage());
            logging("switching");
            switchSolvers();
            return check();
        }
    }

    @Override
    public void push() {

        logging("push");

        assertions.push();
        pushes.add(assertions.toCollection().size());
        solver.push();
    }

    @Override
    public void pop(int n) {

        logging("pop " + n);

        assertions.pop(n);
        solver.pop(n);
        for (int i = 0; i < n; i++) {
            pushes.remove(pushes.size() - 1);
        }
    }

    @Override
    public void reset() {

        logging("reset");

        for (Solver s : solvers) {
            logging("resetting " + s.getClass().getSimpleName());
            s.reset();
        }
        currentSolverIndex = 0;
        solver = solvers.get(currentSolverIndex);
    }

    @Override
    public SolverStatus getStatus() {
        try {
            logging("getting status of " + solver.getClass().getSimpleName());
            return solver.getStatus();
        }
        catch (Exception|Error e) {
            logging("error getting status of " + solver.getClass().getSimpleName() + ": " + e.getMessage());
            switchSolvers();
            check();
            return getStatus();
        }
    }

    @Override
    public Valuation getModel() {
        try {
            logging("getting model of " + solver.getClass().getSimpleName());
            return solver.getModel();
        }
        catch (Exception|Error e) {
            logging("error getting model of " + solver.getClass().getSimpleName() + ": " + e.getMessage());
            switchSolvers();
            check();
            return getModel();
        }
    }

    @Override
    public Collection<Expr<BoolType>> getAssertions() {
        logging("getting assertions");
        return solver.getAssertions();
    }

    @Override
    public void close() throws Exception {
        logging("close");
        for (Solver s : solvers) {
            logging("closing solver " + s.getClass().getSimpleName());
            s.close();
        }
    }

    private void switchSolvers() {
        logging("switching solvers");

        checkState(currentSolverIndex != solvers.size(), "Metasolver has cycled through all of its solvers.");
        try {
            solver.close();
        } catch (Exception e) {
            throw new MetaSolverException("Closing solver failed", e);
        }
        solver = solvers.get(++currentSolverIndex);

        int i = 0;
        for (Expr<BoolType> assertion : assertions) {
            if (pushes.contains(i++)) solver.push();
            solver.add(assertion);
        }
    }

    private void logging(String msg) {
        System.out.println("[MetaSolver] " + msg);
    }

}
