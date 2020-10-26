package experiments;

import heros.InterproceduralCFG;
import org.eclipse.viatra.query.runtime.base.itc.graphimpl.DotGenerator;
import org.eclipse.viatra.query.runtime.base.itc.graphimpl.Graph;
import org.eclipse.viatra.query.runtime.base.itc.igraph.IGraphObserver;
import org.eclipse.viatra.query.runtime.matchers.util.EclipseCollectionsSetMemory;
import org.eclipse.viatra.query.runtime.matchers.util.IMemoryView;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class ICFG
        extends Graph<Unit> {

    private final Set<Unit> nodes;
    private final Map<Unit, EclipseCollectionsSetMemory<Unit>> edges;
    private static final EclipseCollectionsSetMemory<Unit> EMPTY = new EclipseCollectionsSetMemory<>();
    private final InterproceduralCFG<Unit, SootMethod> jicfg;

    public ICFG(final InterproceduralCFG<Unit, SootMethod> jicfg) {
        this.nodes = Collections.synchronizedSet(new HashSet<>());
        this.edges = Collections.synchronizedMap(new HashMap<>());
        this.jicfg = jicfg;
    }

    public SootMethod getMethodOf(final Unit unit) {
        return this.jicfg.getMethodOf(unit);
    }

    @Override
    public void insertEdge(final Unit source, final Unit target) {
        this.insertNode(source);
        this.insertNode(target);
        this.edges.compute(source, (key, value) -> {
            if (value == null) {
                value = new EclipseCollectionsSetMemory<>();
            }
            value.add(target);
            return value;
        });
    }

    @Override
    public void deleteEdgeIfExists(final Unit source, final Unit target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteEdgeThatExists(final Unit source, final Unit target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertNode(final Unit node) {
        this.nodes.add(node);
    }

    @Override
    public void deleteNode(final Unit node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void attachObserver(final IGraphObserver<Unit> go) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void attachAsFirstObserver(final IGraphObserver<Unit> observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void detachObserver(final IGraphObserver<Unit> go) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Unit> getAllNodes() {
        return this.nodes;
    }

    @Override
    public IMemoryView<Unit> getTargetNodes(final Unit source) {
        return this.edges.getOrDefault(source, EMPTY);
    }

    @Override
    public IMemoryView<Unit> getSourceNodes(final Unit target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public void writeToFile(final File output, final JimpleBasedInterproceduralCFG jicfg) {
        try {
            final PrintWriter writer = new PrintWriter(output.getAbsolutePath() + File.separator + "icfg.dot", "UTF-8");
            writer.print(this.toString(jicfg));
            writer.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public String toString(final JimpleBasedInterproceduralCFG jicfg) {
        return DotGenerator.generateDot(this, false,
                unit -> System.identityHashCode(unit) + " " + jicfg.getMethodOf(unit) + "\n" + escape(
                        unit.toString()), null, null);
    }

    private String escape(final String text) {
        return text.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }
}
