package experiments;

import com.google.common.collect.ImmutableMap;
import heros.InterproceduralCFG;
import heros.solver.IFDSSolver;
import org.apache.commons.io.FileUtils;
import org.eclipse.viatra.query.runtime.matchers.tuple.Tuple;
import org.eclipse.viatra.query.runtime.matchers.tuple.Tuples;
import soot.*;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.options.Options;

import java.io.File;
import java.util.Map;

// dot -Tpdf icfg.dot  -o icfg.pdf
public class ICFGTest {

    public static final String BENCHMARK = "minijavac";
    public static final Map<String, Tuple> BENCHMARKS =
            ImmutableMap.<String, Tuple>builder().
                    put("test", Tuples.flatTupleOf("/Users/tamas.szabo/git/TestProject/out/production/TestProject", "Main")).
                    put("minijavac", Tuples.flatTupleOf("/Users/tamas.szabo/git/doop.experiments/benchmarks/minijavac.jar", "Main")).
                    put("ant", Tuples.flatTupleOf("/Users/tamas.szabo/git/doop.experiments/benchmarks/ant-1.8.4.jar", "org.apache.tools.ant.Main")).
                    put("antlr", Tuples.flatTupleOf("/Users/tamas.szabo/git/doop.experiments/benchmarks/antlr4-4.0.jar", "org.antlr.v4.Tool")).
                    put("emma", Tuples.flatTupleOf("/Users/tamas.szabo/git/doop.experiments/benchmarks/emma-2.0.5312.jar", "emma")).
                    put("pmd", Tuples.flatTupleOf("/Users/tamas.szabo/git/doop.experiments/benchmarks/pmd-4.2.5.jar", "net.sourceforge.pmd.PMD")).
                    build();

    public static final String JAVA_HOME_RT = "/Library/Java/JavaVirtualMachines/jdk1.8.0_281.jdk/Contents/Home/jre/lib/rt.jar";
    public static final String OUTPUT_FOLDER = "/Users/tamas.szabo/git/soot.experiments/facts";

    public static void main(final String[] args) {
        final Tuple benchmarkData = BENCHMARKS.get(BENCHMARK);
        final String CLASSPATH = benchmarkData.get(0) + File.pathSeparator + JAVA_HOME_RT;

        G.reset();
        Options.v().set_soot_classpath(CLASSPATH);

        // We want to perform a whole program, i.e. an inter-procedural analysis.
        // We construct a basic CHA call graph for the program
        Options.v().set_whole_program(true);
        Options.v().setPhaseOption("cg.cha", "on");
        Options.v().setPhaseOption("cg", "all-reachable:true");

        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Options.v().set_prepend_classpath(false);

        Scene.v().addBasicClass("java.lang.StringBuilder");
        final SootClass mainClass = Scene.v().forceResolve((String) benchmarkData.get(1), SootClass.BODIES);
        if (mainClass != null) {
            mainClass.setApplicationClass();
        }
        Scene.v().loadNecessaryClasses();

        final Transform transform = new Transform("wjtp.ifds", createFactExtractor());
        PackManager.v().getPack("wjtp").add(transform);

        //Apply all necessary packs of soot. This will execute the respective Transformer
        PackManager.v().getPack("cg").apply();
        PackManager.v().getPack("wjtp").apply();
    }

    private static Transformer createFactExtractor() {
        return new SceneTransformer() {
            @Override
            protected void internalTransform(final String phaseName, final Map<String, String> options) {
                final JimpleBasedInterproceduralCFG jicfg = new JimpleBasedInterproceduralCFG();
                final ICFGExtractor extractor = new ICFGExtractor(jicfg);
                final IFDSSolver<Unit, Number, SootMethod, InterproceduralCFG<Unit, SootMethod>> solver =
                        new IFDSSolver<>(extractor);
                solver.solve();
                final ICFG icfg = extractor.getICFG();

                // cleanup
                try {
                    FileUtils.deleteDirectory(new File(OUTPUT_FOLDER + File.separator + BENCHMARK));
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }

                Database oldDatabase = null;
                Database newDatabase;

                int index = 0;
                do {
                    final File outputFolder = new File(OUTPUT_FOLDER + File.separator + BENCHMARK + File.separator + index);
                    newDatabase = new Facts(icfg).asDatabase();
                    Database.writeToFile(Database.computeDiff(oldDatabase, newDatabase), outputFolder);
                    oldDatabase = newDatabase;
                    index++;
                } while (index <= 1000 && ProgramChanger.tryRewrite(icfg));
            }

        };
    }

}
