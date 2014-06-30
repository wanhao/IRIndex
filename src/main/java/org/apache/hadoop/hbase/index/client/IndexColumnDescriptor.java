package org.apache.hadoop.hbase.index.client;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;

/**
 * IndexTableDescriptor holds additional index information of a table.
 * <p>
 * An index is defined on a column, and each column has no more than one index.
 */
public class IndexColumnDescriptor extends HColumnDescriptor {
  public static final byte[] INDEX = Bytes.toBytes("INDEX");

  // all IndexDescriptors
  public final Map<byte[], IndexDescriptor> indexes = new TreeMap<byte[], IndexDescriptor>(
      Bytes.BYTES_COMPARATOR);

  public IndexColumnDescriptor() {

  }

  public IndexColumnDescriptor(byte[] familyName) {
    super(familyName);
  }

  public IndexColumnDescriptor(String familyName) {
    super(familyName);
  }

  public IndexColumnDescriptor(HColumnDescriptor desc) {
    super(desc);
    if (desc instanceof IndexColumnDescriptor) {
      IndexColumnDescriptor indexDesc = (IndexColumnDescriptor) desc;
      addIndexes(indexDesc.getAllIndex());
    } else {
      byte[] bytes = desc.getValue(INDEX);

      if (bytes != null && bytes.length != 0) {
        DataInputBuffer indexin = new DataInputBuffer();
        indexin.reset(bytes, bytes.length);

        int size;
        try {
          size = indexin.readInt();
          for (int i = 0; i < size; i++) {
            IndexDescriptor indexDescriptor = new IndexDescriptor();
            indexDescriptor.readFields(indexin);
            indexes.put(indexDescriptor.getQualifier(), indexDescriptor);
          }
        } catch (IOException e) {
          e.printStackTrace();
        }

      }

    }
  }

  public Map<byte[], IndexDescriptor> getAllIndexMap() {
    return Collections.unmodifiableMap(indexes);
  }

  public IndexDescriptor[] getAllIndex() {
    return indexes.values().toArray(new IndexDescriptor[0]);
  }

  public IndexDescriptor getIndex(byte[] qualifier) {
    if (!indexes.containsKey(qualifier)) {
      return null;
    }
    return indexes.get(qualifier);
  }

  /**
   * Add an IndexDescriptor to table descriptor.
   * @param index
   */
  public void addIndex(IndexDescriptor index) {
    if (index != null && index.getQualifier() != null) {
      indexes.put(index.getQualifier(), index);
    }
  }

  /**
   * Add IndexDescriptors to table descriptor.
   * @param indexes
   */
  public void addIndexes(IndexDescriptor[] indexes) {
    if (indexes != null && indexes.length != 0) {
      for (IndexDescriptor index : indexes) {
        addIndex(index);
      }
    }
  }

  /**
   * Delete an Index from table descriptor.
   * @param family
   */
  public void deleteIndex(byte[] qualifier) {
    if (indexes.containsKey(qualifier)) {
      indexes.remove(qualifier);
    }
  }

  /**
   * Delete all IndexSpecifications.
   * @throws IOException
   */
  public void deleteAllIndex() throws IOException {
    indexes.clear();
  }

  /**
   * Check if table descriptor contains any index.
   * @return true if has index
   */
  public boolean hasIndex() {
    return !indexes.isEmpty();
  }

  /**
   * Check if table descriptor contains the specific index.
   * @return true if has index
   */
  public boolean containIndex(byte[] qualifier) {
    return indexes.containsKey(qualifier);
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    super.readFields(in);

    DataInputBuffer indexin = new DataInputBuffer();
    byte[] bytes = super.getValue(INDEX);
    indexin.reset(bytes, bytes.length);

    int size = indexin.readInt();
    for (int i = 0; i < size; i++) {
      IndexDescriptor indexDescriptor = new IndexDescriptor();
      indexDescriptor.readFields(indexin);
      indexes.put(indexDescriptor.getQualifier(), indexDescriptor);
    }
  }

  @Override
  public void write(DataOutput out) throws IOException {
    DataOutputBuffer indexout = new DataOutputBuffer();
    indexout.writeInt(indexes.size());
    for (IndexDescriptor indexDescriptor : indexes.values()) {
      indexDescriptor.write(indexout);
    }
    super.setValue(INDEX, indexout.getData());

    super.write(out);
  }

}
