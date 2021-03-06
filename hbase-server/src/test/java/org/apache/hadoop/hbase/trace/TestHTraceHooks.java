package org.apache.hadoop.hbase.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MediumTests;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.cloudera.htrace.Sampler;
import org.cloudera.htrace.Span;
import org.cloudera.htrace.Trace;
import org.cloudera.htrace.TraceTree;
import org.cloudera.htrace.impl.POJOSpanReceiver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.collect.Multimap;

@Category(MediumTests.class)
public class TestHTraceHooks {

  private static final byte[] FAMILY_BYTES = "family".getBytes();
  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final POJOSpanReceiver rcvr = new POJOSpanReceiver();

  @BeforeClass
  public static void before() throws Exception {
    TEST_UTIL.startMiniCluster(2, 3);
    Trace.addReceiver(rcvr);
  }

  @AfterClass
  public static void after() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
    Trace.removeReceiver(rcvr);
  }

  @Test
  public void testTraceCreateTable() throws Exception {
    Span tableCreationSpan = Trace.startSpan("creating table", Sampler.ALWAYS);
    HTable table; 
    try {
      table = TEST_UTIL.createTable("table".getBytes(),
        FAMILY_BYTES);
    } finally {
      tableCreationSpan.stop();
    }

    Collection<Span> spans = rcvr.getSpans();
    TraceTree traceTree = new TraceTree(spans);
    Collection<Span> roots = traceTree.getRoots();

    assertEquals(1, roots.size());
    Span createTableRoot = roots.iterator().next();

    assertEquals("creating table", createTableRoot.getDescription());
    Multimap<Long, Span> spansByParentIdMap = traceTree
        .getSpansByParentIdMap();

    int startsWithHandlingCount = 0;

    for (Span s : spansByParentIdMap.get(createTableRoot.getSpanId())) {
      if (s.getDescription().startsWith("handling")) {
        startsWithHandlingCount++;
      }
    }

    assertTrue(startsWithHandlingCount > 3);
    assertTrue(spansByParentIdMap.get(createTableRoot.getSpanId()).size() > 3);
    assertTrue(spans.size() > 5);
    
    Put put = new Put("row".getBytes());
    put.add(FAMILY_BYTES, "col".getBytes(), "value".getBytes());

    Span putSpan = Trace.startSpan("doing put", Sampler.ALWAYS);
    try {
      table.put(put);
    } finally {
      putSpan.stop();
    }

    spans = rcvr.getSpans();
    traceTree = new TraceTree(spans);
    roots = traceTree.getRoots();

    assertEquals(2, roots.size());
    Span putRoot = null;
    for (Span root : roots) {
      if (root.getDescription().equals("doing put")) {
        putRoot = root;
      }
    }
    
    assertNotNull(putRoot);
  }
}