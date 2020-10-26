package experiments;

import org.eclipse.viatra.query.runtime.matchers.tuple.Tuples;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Facts {

    private Set<Object> seen;
    private Database database;
    private final ICFG icfg;

    public Facts(final ICFG icfg) {
        this.icfg = icfg;
    }

    public Database asDatabase() {
        if (this.database == null) {
            this.database = new Database();
            this.seen = new HashSet<>();
            for (final Unit source : this.icfg.getAllNodes()) {
                generate(source);
                for (final Unit target : this.icfg.getTargetNodes(source).distinctValues()) {
                    this.database.getRelation("ICFG").setHeader(Database.RelationHeader.fromNames("source", "target"));
                    this.database.getRelation("ICFG").insert(Tuples.staticArityFlatTupleOf(h(source), h(target)));
                }
            }
            this.seen.clear();
        }
        return this.database;
    }

    private void generate(final Unit input) {
        final Database.Relation relation = this.database.getRelation(input.getClass().getSimpleName());
        final int inputHash = h(input);
        this.database.getRelation("Unit").setHeader(Database.RelationHeader.fromNames("id"));
        this.database.getRelation("Unit").insert(Tuples.staticArityFlatTupleOf(inputHash));

        final SootMethod method = this.icfg.getMethodOf(input);
        this.database.getRelation("MethodOfUnit").setHeader(Database.RelationHeader.fromNames("unit", "method"));
        this.database.getRelation("MethodOfUnit").insert(Tuples.staticArityFlatTupleOf(inputHash, h(method)));
        generate(method);

        if (input instanceof JIdentityStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "left", "right"));
            final Value left = ((JIdentityStmt) input).getLeftOp();
            final Value right = ((JIdentityStmt) input).getRightOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(left), h(right)));
            generate(left, input);
            generate(right, input);
        } else if (input instanceof JAssignStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "left", "right"));
            final Value left = ((JAssignStmt) input).getLeftOp();
            final Value right = ((JAssignStmt) input).getRightOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(left), h(right)));
            generate(left, input);
            generate(right, input);
        } else if (input instanceof JReturnStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op", "method"));
            final Value op = ((JReturnStmt) input).getOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op), h(method)));
            generate(op, input);
        } else if (input instanceof JReturnVoidStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "method"));
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(method)));
        } else if (input instanceof JInvokeStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "invokeExpr"));
            final InvokeExpr invokeExpr = ((JInvokeStmt) input).getInvokeExpr();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(invokeExpr)));
            generate(invokeExpr, input);
        } else if (input instanceof JIfStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "condition", "target"));
            final Value condition = ((JIfStmt) input).getCondition();
            final Stmt target = ((JIfStmt) input).getTarget();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(condition), h(target)));
            generate(condition, input);
            generate(target);
        } else if (input instanceof JGotoStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "target"));
            final Unit target = ((JGotoStmt) input).getTarget();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(target)));
            generate(target);
        } else if (input instanceof JTableSwitchStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "key", "defaultCase"));
            final Value key = ((JTableSwitchStmt) input).getKey();
            final Unit defaultCase = ((JTableSwitchStmt) input).getDefaultTarget();
            final List<Unit> targets = ((JTableSwitchStmt) input).getTargets();
            final int low = ((JTableSwitchStmt) input).getLowIndex();
            final int high = ((JTableSwitchStmt) input).getHighIndex();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(key), h(defaultCase)));
            generate(key, input);
            generate(defaultCase);
            for (int i = 0; i <= high - low; i++) {
                final Unit target = targets.get(i);
                this.database.getRelation("TableSwitchCases").setHeader(Database.RelationHeader.fromNames("switchId", "index", "target"));
                this.database.getRelation("TableSwitchCases").insert(Tuples.staticArityFlatTupleOf(inputHash, low + i, h(target)));
                generate(target);
            }
        } else if (input instanceof JLookupSwitchStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "key", "defaultCase"));
            final Value key = ((JLookupSwitchStmt) input).getKey();
            final Unit defaultCase = ((JLookupSwitchStmt) input).getDefaultTarget();
            final List<Unit> targets = ((JLookupSwitchStmt) input).getTargets();
            final List<IntConstant> lookupValues = ((JLookupSwitchStmt) input).getLookupValues();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(key), h(defaultCase)));
            generate(key, input);
            generate(defaultCase);
            for (int i = 0; i < lookupValues.size(); i++) {
                final IntConstant lookupValue = lookupValues.get(i);
                final Unit target = targets.get(i);
                this.database.getRelation("LookupSwitchCases").setHeader(Database.RelationHeader.fromNames("switchId", "lookupValue", "target"));
                this.database.getRelation("LookupSwitchCases").insert(Tuples.staticArityFlatTupleOf(inputHash, h(lookupValue), h(target)));
                generate(target);
                generate(lookupValue, input);
            }
        } else if (input instanceof JThrowStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op"));
            final Value op = ((JThrowStmt) input).getOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op)));
            generate(op, input);
        } else if (input instanceof JExitMonitorStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op"));
            final Value op = ((JExitMonitorStmt) input).getOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op)));
            generate(op, input);
        } else if (input instanceof JEnterMonitorStmt) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op"));
            final Value op = ((JEnterMonitorStmt) input).getOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op)));
            generate(op, input);
        } else {
            throw new IllegalArgumentException("Unhandled unit kind " + input.getClass());
        }
    }

    public void generate(final Value input, final Unit surroundingUnit) {
        final Database.Relation relation = this.database.getRelation(input.getClass().getSimpleName());
        final int inputHash = h(input);
        this.database.getRelation("Value").setHeader(Database.RelationHeader.fromNames("id"));
        this.database.getRelation("Value").insert(Tuples.staticArityFlatTupleOf(inputHash));
        if (input instanceof JimpleLocal) {
            if (this.seen.add(input)) {
                this.database.getRelation("MethodOfJimpleLocal").setHeader(Database.RelationHeader.fromNames("local", "method"));
                final SootMethod method = this.icfg.getMethodOf(surroundingUnit);
                this.database.getRelation("MethodOfJimpleLocal").insert(Tuples.staticArityFlatTupleOf(inputHash, h(method)));
                generate(method);
            }
            relation.setHeader(Database.RelationHeader.fromPairs("id", Integer.class, "name", String.class, "type", Integer.class));
            final String name = ((JimpleLocal) input).getName();
            final Type type = input.getType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, name, h(type)));
            generate(type);
        } else if (input instanceof ParameterRef) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "index", "type", "method"));
            final SootMethod method = this.icfg.getMethodOf(surroundingUnit);
            final int index = ((ParameterRef) input).getIndex();
            final Type type = input.getType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, index, h(type), h(method)));
            generate(type);
            generate(method);
        } else if (input instanceof ThisRef) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "type"));
            final Type type = input.getType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(type)));
            generate(type);
        } else if (input instanceof InstanceFieldRef) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "base", "fieldRef"));
            final SootField fieldRef = ((InstanceFieldRef) input).getField();
            final Value base = ((InstanceFieldRef) input).getBase();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(base), h(fieldRef)));
            generate(base, surroundingUnit);
            generate(fieldRef);
        } else if (input instanceof StaticFieldRef) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "fieldRef"));
            final SootField fieldRef = ((StaticFieldRef) input).getField();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(fieldRef)));
            generate(fieldRef);
        } else if (input instanceof InstanceInvokeExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "base", "methodRef"));
            final Value base = ((InstanceInvokeExpr) input).getBase();
            final List<Value> arguments = ((InstanceInvokeExpr) input).getArgs();
            final SootMethod methodRef = ((InstanceInvokeExpr) input).getMethod();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(base), h(methodRef)));
            generate(base, surroundingUnit);
            generate(methodRef);
            generateMethodInvocationArguments(surroundingUnit, inputHash, arguments);
        } else if (input instanceof StaticInvokeExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "methodRef"));
            final List<Value> arguments = ((StaticInvokeExpr) input).getArgs();
            final SootMethod methodRef = ((StaticInvokeExpr) input).getMethod();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(methodRef)));
            generate(methodRef);
            generateMethodInvocationArguments(surroundingUnit, inputHash, arguments);
        } else if (input instanceof StringConstant) {
            relation.setHeader(Database.RelationHeader.fromPairs("id", Integer.class, "value", String.class));
            final String value = ((StringConstant) input).value;
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, value.replaceAll("\\s", "")));
        } else if (input instanceof IntConstant) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "value"));
            final int value = ((IntConstant) input).value;
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, value));
        } else if (input instanceof DoubleConstant) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "value"));
            final double value = ((DoubleConstant) input).value;
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, Double.toString(value)));
        } else if (input instanceof LongConstant) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "value"));
            final long value = ((LongConstant) input).value;
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, value));
        } else if (input instanceof FloatConstant) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "value"));
            final float value = ((FloatConstant) input).value;
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, Float.toString(value)));
        } else if (input instanceof AbstractBinopExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "left", "right"));
            final Value left = ((AbstractBinopExpr) input).getOp1();
            final Value right = ((AbstractBinopExpr) input).getOp2();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(left), h(right)));
            generate(left, surroundingUnit);
            generate(right, surroundingUnit);
        } else if (input instanceof JNewExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "type"));
            final Type type = input.getType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(type)));
            generate(type);
        } else if (input instanceof JArrayRef) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "base", "index"));
            final Value base = ((JArrayRef) input).getBase();
            final Value index = ((JArrayRef) input).getIndex();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(base), h(index)));
            generate(base, surroundingUnit);
            generate(index, surroundingUnit);
        } else if (input instanceof JCastExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op", "type"));
            final Value op = ((JCastExpr) input).getOp();
            final Type type = input.getType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op), h(type)));
            generate(op, surroundingUnit);
            generate(type);
        } else if (input instanceof NullConstant) {
            relation.setHeader(Database.RelationHeader.fromNames("id"));
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash));
        } else if (input instanceof JCaughtExceptionRef) {
            relation.setHeader(Database.RelationHeader.fromNames("id"));
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash));
        } else if (input instanceof JNewArrayExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "baseType", "size"));
            final Type baseType = ((JNewArrayExpr) input).getBaseType();
            final Value size = ((JNewArrayExpr) input).getSize();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(baseType), h(size)));
            generate(baseType);
            generate(size, surroundingUnit);
        } else if (input instanceof JLengthExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op"));
            final Value op = ((JLengthExpr) input).getOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op)));
            generate(op, surroundingUnit);
        } else if (input instanceof JNegExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op"));
            final Value op = ((JNegExpr) input).getOp();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op)));
            generate(op, surroundingUnit);
        } else if (input instanceof JInstanceOfExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "op", "type"));
            final Value op = ((JInstanceOfExpr) input).getOp();
            final Type type = ((JInstanceOfExpr) input).getCheckType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(op), h(type)));
            generate(op, surroundingUnit);
            generate(type);
        } else if (input instanceof JNewMultiArrayExpr) {
            relation.setHeader(Database.RelationHeader.fromNames("id", "baseType"));
            final ArrayType baseType = ((JNewMultiArrayExpr) input).getBaseType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(baseType)));
            generate(baseType);
            for (int i = 0; i < ((JNewMultiArrayExpr) input).getSizeCount(); i++) {
                final Value size = ((JNewMultiArrayExpr) input).getSize(i);
                this.database.getRelation("NewMultiArraySizes").setHeader(Database.RelationHeader.fromNames("arrayExprId", "index", "sizeValueId"));
                this.database.getRelation("NewMultiArraySizes").insert(Tuples.staticArityFlatTupleOf(inputHash, i, h(size)));
                generate(size, surroundingUnit);
            }
        } else if (input instanceof ClassConstant) {
            relation.setHeader(Database.RelationHeader.fromPairs("id", Integer.class, "value", String.class));
            final String value = ((ClassConstant) input).value;
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, value));
        } else {
            throw new IllegalArgumentException("Unhandled value kind " + input.getClass());
        }
    }

    private void generateMethodInvocationArguments(final Unit surroundingUnit, final int inputHash, final List<Value> arguments) {
        int index = 0;
        for (final Value argument : arguments) {
            this.database.getRelation("MethodInvocationArguments").setHeader(Database.RelationHeader.fromNames("invokeId", "index", "argument"));
            this.database.getRelation("MethodInvocationArguments").insert(Tuples.staticArityFlatTupleOf(inputHash, index++, h(argument)));
            generate(argument, surroundingUnit);
        }
    }

    public void generate(final SootField input) {
        if (this.seen.add(input)) {
            final Database.Relation relation = this.database.getRelation(input.getClass().getSimpleName());
            relation.setHeader(Database.RelationHeader.fromPairs("id", Integer.class, "name", String.class, "declaringClass", String.class, "type", Integer.class));
            final int inputHash = h(input);
            final SootClass declaringClass = input.getDeclaringClass();
            final String name = input.getName();
            final Type type = input.getType();
            relation.insert(Tuples.staticArityFlatTupleOf(inputHash, name, declaringClass.getName(), h(type)));
            generate(type);
        }
    }

    public void generate(final SootMethod input) {
        if (this.seen.add(input)) {
            final Database.Relation relation = this.database.getRelation(input.getClass().getSimpleName());
            relation.setHeader(Database.RelationHeader.fromPairs("id", Integer.class, "name", String.class, "declaringClass", String.class, "isStatic", Boolean.class, "returnType", Integer.class));
            final int inputHash = h(input);
            final SootClass declaringClass = input.getDeclaringClass();
            final String name = input.getName();
            final List<Type> parameterTypes = input.getParameterTypes();
            final boolean isStatic = input.isStatic();
            final Type returnType = input.getReturnType();
            relation.insert(Tuples.flatTupleOf(inputHash, name, declaringClass.getName(), isStatic, h(returnType)));
            generate(returnType);
            int index = 0;
            for (final Type parameterType : parameterTypes) {
                this.database.getRelation("MethodParameters").setHeader(Database.RelationHeader.fromNames("methodId", "index", "parameterType"));
                this.database.getRelation("MethodParameters").insert(Tuples.staticArityFlatTupleOf(inputHash, index++, h(parameterType)));
                generate(parameterType);
            }
        }
    }

    public void generate(final Type input) {
        if (this.seen.add(input)) {
            final Database.Relation relation = this.database.getRelation(input.getClass().getSimpleName());
            final int inputHash = h(input);
            if (input instanceof RefType) {
                relation.setHeader(Database.RelationHeader.fromPairs("id", Integer.class, "name", String.class));
                final String name = ((RefType) input).getClassName();
                relation.insert(Tuples.staticArityFlatTupleOf(inputHash, name));
            } else if (input instanceof PrimType || input instanceof VoidType) {
                relation.setHeader(Database.RelationHeader.fromNames("id"));
                relation.insert(Tuples.staticArityFlatTupleOf(inputHash));
            } else if (input instanceof ArrayType) {
                relation.setHeader(Database.RelationHeader.fromNames("id", "baseType"));
                final Type baseType = ((ArrayType) input).getElementType();
                relation.insert(Tuples.staticArityFlatTupleOf(inputHash, h(baseType)));
                generate(baseType);
            } else {
                throw new IllegalArgumentException("Unhandled type kind " + input.getClass());
            }
        }
    }

    private int h(final Object input) {
        return System.identityHashCode(input);
    }

}
