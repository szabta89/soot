package experiments;

import com.google.common.collect.ImmutableList;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import soot.IntType;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.scalar.Pair;

import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProgramChanger {

    public static final Change REPLACE_INT_CONSTANT = new Change() {

        @Override
        public boolean test(final Value node, final PVector<Object> ancestors) {
            if (node instanceof IntConstant) {
                final Object last = ancestors.get(ancestors.size() - 1);
                return !(last instanceof JArrayRef) && ((IntConstant) node).value != 0;
            }
            return false;
        }

        @Override
        public Value apply(final Value input) {
            return IntConstant.v(0);
        }

        @Override
        public String getName() {
            return "REPLACE_INT_CONSTANT";
        }
    };

    public static final Change REPLACE_FIELD_REFERENCE = new Change() {

        @Override
        public boolean test(final Value node, final PVector<Object> ancestors) {
            if (node instanceof FieldRef && ((FieldRef) node).getField().getType() instanceof IntType) {
                final Object last = ancestors.get(ancestors.size() - 1);
                final boolean c1 = last instanceof JAssignStmt && ((JAssignStmt) last).getLeftOp() == node;
                final boolean c2 = last instanceof JIdentityStmt && ((JIdentityStmt) last).getLeftOp() == node;
                return !(c1 || c2);
            }
            return false;
        }

        @Override
        public Value apply(final Value input) {
            return IntConstant.v(0);
        }

        @Override
        public String getName() {
            return "REPLACE_FIELD_REFERENCE";
        }

    };

    public static final Random RANDOM = new Random(1563540296429L);
    private static final List<Change> CHANGES = ImmutableList.of(REPLACE_INT_CONSTANT, REPLACE_FIELD_REFERENCE);

    public static boolean tryRewrite(final ICFG input) {
        for (final Unit unit : input.getAllNodes()) {
            if (tryRewrite(unit, TreePVector.empty())) {
                return true;
            }
        }
        return false;
    }

    public static boolean tryRewrite(final Unit input, final PVector<Object> ancestors) {
        final PVector<Object> newAncestors = ancestors.plus(input);
        if (input instanceof JIdentityStmt) {
            if (tryRewriteGuard(((JIdentityStmt) input).getLeftOp(), ((JIdentityStmt) input)::setLeftOp, newAncestors)) {
                return true;
            }
            return tryRewriteGuard(((JIdentityStmt) input).getRightOp(), ((JIdentityStmt) input)::setRightOp, newAncestors);
        } else if (input instanceof JAssignStmt) {
            if (tryRewriteGuard(((JAssignStmt) input).getLeftOp(), ((JAssignStmt) input)::setLeftOp, newAncestors)) {
                return true;
            }
            return tryRewriteGuard(((JAssignStmt) input).getRightOp(), ((JAssignStmt) input)::setRightOp, newAncestors);
        } else if (input instanceof JReturnStmt) {
            return tryRewriteGuard(((JReturnStmt) input).getOp(), ((JReturnStmt) input)::setOp, newAncestors);
        } else if (input instanceof JReturnVoidStmt) {
            return false;
        } else if (input instanceof JInvokeStmt) {
            return tryRewriteGuard(((JInvokeStmt) input).getInvokeExpr(), ((JInvokeStmt) input)::setInvokeExpr, newAncestors);
        } else if (input instanceof JIfStmt) {
            if (tryRewriteGuard(((JIfStmt) input).getCondition(), ((JIfStmt) input)::setCondition, newAncestors)) {
                return true;
            }
            return tryRewrite(((JIfStmt) input).getTarget(), newAncestors);
        } else if (input instanceof JGotoStmt) {
            return tryRewrite(((JGotoStmt) input).getTarget(), newAncestors);
        } else if (input instanceof JTableSwitchStmt) {
            if (tryRewriteGuard(((JTableSwitchStmt) input).getKey(), ((JTableSwitchStmt) input)::setKey, newAncestors)) {
                return true;
            }
            for (final Unit target : ((JTableSwitchStmt) input).getTargets()) {
                if (tryRewrite(target, newAncestors)) {
                    return true;
                }
            }
            return tryRewrite(((JTableSwitchStmt) input).getDefaultTarget(), newAncestors);
        } else if (input instanceof JLookupSwitchStmt) {
            if (tryRewriteGuard(((JLookupSwitchStmt) input).getKey(), ((JLookupSwitchStmt) input)::setKey, newAncestors)) {
                return true;
            }
            for (final Unit target : ((JLookupSwitchStmt) input).getTargets()) {
                if (tryRewrite(target, newAncestors)) {
                    return true;
                }
            }
            return tryRewrite(((JLookupSwitchStmt) input).getDefaultTarget(), newAncestors);
        } else if (input instanceof JThrowStmt) {
            return tryRewriteGuard(((JThrowStmt) input).getOp(), ((JThrowStmt) input)::setOp, newAncestors);
        } else if (input instanceof JExitMonitorStmt) {
            return tryRewriteGuard(((JExitMonitorStmt) input).getOp(), ((JExitMonitorStmt) input)::setOp, newAncestors);
        } else if (input instanceof JEnterMonitorStmt) {
            return tryRewriteGuard(((JEnterMonitorStmt) input).getOp(), ((JEnterMonitorStmt) input)::setOp, newAncestors);
        } else {
            throw new IllegalArgumentException("Unhandled unit kind " + input.getClass());
        }
    }

    public static Pair<Value, Boolean> tryRewrite(final Value input, final PVector<Object> ancestors) {
        final PVector<Object> newAncestors = ancestors.plus(input);
        if (input instanceof JimpleLocal) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof ParameterRef) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof ThisRef) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof InstanceFieldRef) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof StaticFieldRef) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof InstanceInvokeExpr) {
            if (tryRewriteGuard(((InstanceInvokeExpr) input).getBase(), ((InstanceInvokeExpr) input)::setBase, newAncestors)) {
                return new Pair<>(null, true);
            }
            final List<Value> arguments = ((InstanceInvokeExpr) input).getArgs();
            for (int i = 0; i < arguments.size(); i++) {
                final int finalIndex = i;
                if (tryRewriteGuard(arguments.get(i), a -> ((InstanceInvokeExpr) input).setArg(finalIndex, a), newAncestors)) {
                    return new Pair<>(null, true);
                }
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof StaticInvokeExpr) {
            final List<Value> arguments = ((StaticInvokeExpr) input).getArgs();
            for (int i = 0; i < arguments.size(); i++) {
                final int finalIndex = i;
                if (tryRewriteGuard(arguments.get(i), a -> ((StaticInvokeExpr) input).setArg(finalIndex, a), newAncestors)) {
                    return new Pair<>(null, true);
                }
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof StringConstant) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof IntConstant) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof DoubleConstant) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof LongConstant) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof FloatConstant) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof AbstractBinopExpr) {
            if (tryRewriteGuard(((AbstractBinopExpr) input).getOp1(), ((AbstractBinopExpr) input)::setOp1, newAncestors)) {
                return new Pair<>(null, true);
            }
            if (tryRewriteGuard(((AbstractBinopExpr) input).getOp1(), ((AbstractBinopExpr) input)::setOp1, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JNewExpr) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JArrayRef) {
            if (tryRewriteGuard(((JArrayRef) input).getIndex(), ((JArrayRef) input)::setIndex, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JCastExpr) {
            if (tryRewriteGuard(((JCastExpr) input).getOp(), ((JCastExpr) input)::setOp, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof NullConstant) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JCaughtExceptionRef) {
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JNewArrayExpr) {
            if (tryRewriteGuard(((JNewArrayExpr) input).getSize(), ((JNewArrayExpr) input)::setSize, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JLengthExpr) {
            if (tryRewriteGuard(((JLengthExpr) input).getOp(), ((JLengthExpr) input)::setOp, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JNegExpr) {
            if (tryRewriteGuard(((JNegExpr) input).getOp(), ((JNegExpr) input)::setOp, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JInstanceOfExpr) {
            if (tryRewriteGuard(((JInstanceOfExpr) input).getOp(), ((JInstanceOfExpr) input)::setOp, newAncestors)) {
                return new Pair<>(null, true);
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof JNewMultiArrayExpr) {
            for (int i = 0; i < ((JNewMultiArrayExpr) input).getSizeCount(); i++) {
                final int finalIndex = i;
                final Value size = ((JNewMultiArrayExpr) input).getSize(i);
                if (tryRewriteGuard(size, s -> ((JNewMultiArrayExpr) input).setSize(finalIndex, s), newAncestors)) {
                    return new Pair<>(null, true);
                }
            }
            return tryRewriteRandom(input, ancestors);
        } else if (input instanceof ClassConstant) {
            return tryRewriteRandom(input, ancestors);
        } else {
            throw new IllegalArgumentException("Unhandled value kind " + input.getClass());
        }
    }

    public static Pair<Value, Boolean> tryRewriteRandom(final Value input, final PVector<Object> ancestors) {
        final List<Change> applicableChanges = CHANGES.stream().filter(c -> c.test(input, ancestors)).collect(Collectors.toList());
        if (!applicableChanges.isEmpty()) {
            final int index = RANDOM.nextInt(applicableChanges.size());
            final Change change = applicableChanges.get(index);
            //System.out.println("Rewriting " + input + " (" + ancestors + ") with " + change);
            return new Pair<>(change.apply(input), true);
        } else {
            return new Pair<>(null, false);
        }
    }

    public static boolean tryRewriteGuard(final Value value, final Consumer<Value> consumer, final PVector<Object> ancestors) {
        final Pair<Value, Boolean> pair = tryRewrite(value, ancestors);
        final Value rewrittenValue = pair.getO1();
        if (rewrittenValue != null) {
            consumer.accept(rewrittenValue);
            return true;
        } else {
            return pair.getO2();
        }
    }

    public abstract static class Change implements BiPredicate<Value, PVector<Object>>, Function<Value, Value> {

        public abstract String getName();

    }

}
