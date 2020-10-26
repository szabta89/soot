package experiments;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.eclipse.viatra.query.runtime.matchers.tuple.Tuple;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class Database {

    private final Map<String, Relation> relations;

    public Database() {
        this.relations = new HashMap<>();
    }

    public Map<String, Relation> getRelations() {
        return relations;
    }

    public static void writeToFile(final Map<String, ? extends Relation> relations, final File outputFolder) {
        if (outputFolder.exists()) {
            try {
                FileUtils.deleteDirectory(outputFolder);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        outputFolder.mkdirs();

        for (final Map.Entry<String, ? extends Relation> entry : relations.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                try {
                    final PrintWriter writer = new PrintWriter(outputFolder.getAbsoluteFile() + File.separator + entry.getKey() + ".facts", "UTF-8");
                    writer.print(entry.getValue().toString());
                    writer.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class Relation {
        protected final Set<Tuple> insertedTuples;
        protected RelationHeader header;

        public Relation() {
            this.insertedTuples = new HashSet<>();
        }

        public void insert(final Tuple tuple) {
            if (this.header != null && this.header.columns.size() != tuple.getSize()) {
                throw new IllegalArgumentException();
            }
            this.insertedTuples.add(tuple);
        }

        public void setHeader(final RelationHeader header) {
            if (this.header == null) {
                this.header = header;
            }
        }

        public boolean isEmpty() {
            return this.insertedTuples.isEmpty();
        }

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append(this.header.toString()).append("\n");
            int index = 0;
            for (final Tuple tuple : this.insertedTuples) {
                buffer.append(toString(tuple));
                if (index++ < this.insertedTuples.size() - 1) {
                    buffer.append("\n");
                }
            }
            return buffer.toString();
        }

        protected String toString(final Tuple tuple) {
            final StringBuilder buffer = new StringBuilder();
            boolean first = true;
            for (final Object obj : tuple.getElements()) {
                if (first) {
                    first = false;
                } else {
                    buffer.append("\t");
                }
                buffer.append(obj);
            }
            return buffer.toString();
        }
    }

    public static class RelationDiff extends Relation {
        protected final Set<Tuple> deletedTuples;

        public RelationDiff() {
            super();
            this.deletedTuples = new HashSet<>();
        }

        public void delete(final Tuple tuple) {
            if (this.header != null && this.header.columns.size() != tuple.getSize()) {
                throw new IllegalArgumentException();
            }
            this.deletedTuples.add(tuple);
        }

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append(this.header.toString()).append("\n");
            int index = 0;
            for (final Tuple tuple : this.deletedTuples) {
                buffer.append("-");
                buffer.append(toString(tuple));
                if (!this.insertedTuples.isEmpty() || index < this.deletedTuples.size() - 1) {
                    buffer.append("\n");
                }
                index++;
            }
            index = 0;
            for (final Tuple tuple : this.insertedTuples) {
                buffer.append("+");
                buffer.append(toString(tuple));
                if (index < this.insertedTuples.size() - 1) {
                    buffer.append("\n");
                }
                index++;
            }
            return buffer.toString();
        }

        @Override
        public boolean isEmpty() {
            return this.insertedTuples.isEmpty() && this.deletedTuples.isEmpty();
        }
    }

    public static class RelationHeader {
        private final List<Column> columns;

        private RelationHeader(final List<Column> columns) {
            this.columns = columns;
        }

        public static RelationHeader fromPairs(final Object... values) {
            final List<Column> columns = new ArrayList<>();
            for (int i = 0; i < values.length / 2; i++) {
                columns.add(new Column((String) values[2 * i], (Class<?>) values[2 * i + 1]));
            }
            return new RelationHeader(columns);
        }

        public static RelationHeader fromNames(final String... values) {
            final List<Column> columns = new ArrayList<>();
            for (final String value : values) {
                columns.add(new Column(value, Integer.class));
            }
            return new RelationHeader(columns);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (final Column column : this.columns) {
                if (first) {
                    first = false;
                } else {
                    builder.append("\t");
                }
                builder.append(column.name).append(":").append(column.type.getSimpleName());
            }
            return builder.toString();
        }
    }

    public static class Column {
        public final String name;
        public final Class<?> type;

        public Column(final String name, final Class<?> type) {
            this.name = name;
            this.type = type;
        }
    }

    public Relation getRelation(final String key) {
        return this.relations.compute(key, (k, v) -> {
            if (v == null) {
                v = new Relation();
            }
            return v;
        });
    }

    public static Map<String, RelationDiff> computeDiff(final Database leftDb, final Database rightDb) {
        final Map<String, RelationDiff> result = new HashMap<>();
        for (final String relationName : Sets.union(leftDb == null ? Collections.emptySet() : leftDb.relations.keySet(),
                rightDb == null ? Collections.emptySet() : rightDb.relations.keySet())) {
            final Relation leftRel = leftDb == null ? null : leftDb.relations.get(relationName);
            final Relation rightRel = rightDb == null ? null : rightDb.relations.get(relationName);

            if (leftRel == null && rightRel != null) {
                // entirely new relation inserted
                final RelationDiff diff = new RelationDiff();
                diff.setHeader(rightRel.header);
                diff.insertedTuples.addAll(rightRel.insertedTuples);
                result.put(relationName, diff);
            } else if (leftRel != null && rightRel == null) {
                // entire old relation got deleted
                final RelationDiff diff = new RelationDiff();
                diff.setHeader(leftRel.header);
                diff.deletedTuples.addAll(leftRel.insertedTuples);
                result.put(relationName, diff);
            } else {
                assert leftRel != null;
                // compute actual diff in terms of deleted/inserted tuples
                final RelationDiff diff = new RelationDiff();
                diff.setHeader(leftRel.header);
                final Sets.SetView<Tuple> symDiff = Sets.symmetricDifference(leftRel.insertedTuples, rightRel.insertedTuples);
                for (final Tuple tuple : symDiff) {
                    if (leftRel.insertedTuples.contains(tuple)) {
                        // the tuple has been deleted
                        diff.deletedTuples.add(tuple);
                    } else {
                        // the tuple has been inserted
                        diff.insertedTuples.add(tuple);
                    }
                }
                result.put(relationName, diff);
            }

        }
        return result;
    }
}
