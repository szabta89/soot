package experiments;

import heros.DefaultSeeds;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.InterproceduralCFG;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.DefaultJimpleIFDSTabulationProblem;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

public class ICFGExtractor
    extends DefaultJimpleIFDSTabulationProblem<Number, InterproceduralCFG<Unit, SootMethod>>
    implements FlowFunctions<Unit, Number, SootMethod> {

    final private ICFG jicfg;
    final private static Set<Number> ZERO_SET = Sets.newHashSet(0);
    final private FlowFunction<Number> zeroFunction = number -> ZERO_SET;

    public ICFGExtractor(final InterproceduralCFG<Unit, SootMethod> jicfg) {
        super(jicfg);
        this.jicfg = new ICFG(jicfg);
    }

    private void insertEdge(final Unit source, final Unit target) {
        this.jicfg.insertNode(source);
        this.jicfg.insertNode(target);
        this.jicfg.insertEdge(source, target);
    }

    @Override
    public FlowFunction<Number> getNormalFlowFunction(final Unit predecessor, final Unit successor) {
        insertEdge(predecessor, successor);
        return zeroFunction;
    }

    @Override
    public FlowFunction<Number> getCallFlowFunction(final Unit callUnit, final SootMethod calledMethod) {
        insertEdge(callUnit, calledMethod.getActiveBody().getUnits().getFirst());
        return zeroFunction;
    }

    @Override
    public FlowFunction<Number> getReturnFlowFunction(final Unit callUnit,
                                                      final SootMethod calledMethod,
                                                      final Unit exitUnit,
                                                      final Unit returnUnit) {
        insertEdge(exitUnit, returnUnit);
        return zeroFunction;
    }

    @Override
    public FlowFunction<Number> getCallToReturnFlowFunction(final Unit callUnit, final Unit returnUnit) {
        insertEdge(callUnit, returnUnit);
        return zeroFunction;
    }

    @Override
    protected FlowFunctions<Unit, Number, SootMethod> createFlowFunctionsFactory() {
        return this;
    }

    @Override
    protected Number createZeroValue() {
        return 0;
    }

    @Override
    public Map<Unit, Set<Number>> initialSeeds() {
        return DefaultSeeds.make(Collections.singleton(Scene.v().getMainMethod().getActiveBody().getUnits().getFirst()),
            zeroValue());
    }

    public ICFG getICFG() {
        return this.jicfg;
    }

}
