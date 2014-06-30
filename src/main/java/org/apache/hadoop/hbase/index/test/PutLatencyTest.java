package org.apache.hadoop.hbase.index.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.index.client.DataType;
import org.apache.hadoop.hbase.index.client.IndexColumnDescriptor;
import org.apache.hadoop.hbase.index.client.IndexDescriptor;
import org.apache.hadoop.hbase.util.Bytes;

public class PutLatencyTest {
  static String filePath = "/opt/tpch-test-data/large/xaa";
  static boolean wal = false;
  static int index = 3;
  static int writeNum = 100000;

  ArrayList<Put> queue = new ArrayList<Put>();
  String tableName = "orders";
  Configuration conf = HBaseConfiguration.create();

  Writer writer = null;
  long startTime = 0;
  int writeCount = 0;

  public PutLatencyTest() throws IOException {
    Configuration conf = HBaseConfiguration.create();
    HBaseAdmin admin = new HBaseAdmin(conf);

    if (admin.tableExists(tableName)) {
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
      // return;
    }

    HTableDescriptor tableDesc = new HTableDescriptor(tableName);

    if (index == 1) {
      IndexDescriptor index1 = new IndexDescriptor(Bytes.toBytes("c3"), DataType.DOUBLE);
      IndexDescriptor index2 = new IndexDescriptor(Bytes.toBytes("c4"), DataType.STRING);
      IndexDescriptor index3 = new IndexDescriptor(Bytes.toBytes("c5"), DataType.STRING);

      IndexColumnDescriptor family = new IndexColumnDescriptor("f");
      family.addIndex(index1);
      family.addIndex(index2);
      family.addIndex(index3);

      tableDesc.addFamily(family);
      admin.createTable(tableDesc, Bytes.toBytes("1"), Bytes.toBytes("9"), 10);
    } else {
      HColumnDescriptor family = new HColumnDescriptor("f");
      tableDesc.addFamily(family);
      admin.createTable(tableDesc, Bytes.toBytes("1"), Bytes.toBytes("9"), 10);
    }

    admin.close();
  }

  public void start() throws IOException {
    writer = new Writer();
    writer.setName("Writer");
    writer.start();
  }

  public void stop() {
    try {
      writer.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void report() {
    if (writeCount == 0) return;

    System.out.println("time=" + ((System.nanoTime() - startTime) / 1000) + ",write=" + writeCount
        + ", latency=" + ((System.nanoTime() - startTime) / writeCount / 1000));
  }

  class Writer extends Thread {

    private byte[] reverse(byte[] b) {
      for (int i = 0, j = b.length - 1; i < j; i++, j--) {
        byte tmp = b[i];
        b[i] = b[j];
        b[j] = tmp;
      }
      return b;
    }

    public void loadData() {
      try {
        BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)));
        String line = null, col[] = null;

        // key ORDERKEY Int
        // c1 CUSTKEY Int
        // c2 ORDERSTATUS String
        // c3 TOTALPRICE Double index
        // c4 ORDERDATE String index
        // c5 ORDERPRIORITY String index
        // c6 CLERK String
        // c7 SHIPPRIORITY Int
        // c8 COMMENT String
        while ((line = reader.readLine()) != null) {
          col = line.split("\\|");
          Put put = new Put(reverse(Bytes.toBytes(col[0])));
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c1"), Bytes.toBytes(Integer.valueOf(col[1]))); // int
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c2"), Bytes.toBytes(col[2])); // string
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c3"), Bytes.toBytes(Double.valueOf(col[3]))); // double
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c4"), Bytes.toBytes(col[4])); // string
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c5"), Bytes.toBytes(col[5])); // string
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c6"), Bytes.toBytes(col[6])); // string
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c7"), Bytes.toBytes(Integer.valueOf(col[7]))); // int
          put.add(Bytes.toBytes("f"), Bytes.toBytes("c8"), Bytes.toBytes(col[8])); // string

          if (!wal) {
            put.setDurability(Durability.SKIP_WAL);
          }
          queue.add(put);

          if (queue.size() > writeNum) {
            break;
          }
        }

        reader.close();

        System.out.println("queue:" + queue.size());
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

    }

    public void run() {
      loadData();
      try {
        long stopTime = 0;

        HTable table = new HTable(conf, tableName);
        startTime = System.nanoTime();
        for (Put put : queue) {
          table.put(put);
          writeCount++;
        }
        stopTime = System.nanoTime();
        table.close();

        System.out.println((stopTime - startTime) / writeCount / 1000);

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) throws IOException {
    try {
      filePath = args[0];
      writeNum = Integer.valueOf(args[1]);
      wal = Boolean.valueOf(args[2]);
      index = Integer.valueOf(args[3]);
    } catch (Exception e) {
      System.out.println("filePath  writeNum  wal index");
      // return;
    }

    System.out.println("----------------" + filePath);
    System.out.println("----------------" + writeNum);
    System.out.println("----------------" + wal);
    System.out.println("----------------" + index);

    PutLatencyTest test = new PutLatencyTest();
    test.start();
    while (test.writer.isAlive()) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      test.report();
    }

    test.report();
  }

}
