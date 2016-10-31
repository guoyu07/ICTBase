package ict.ictbase.coprocessor;

import ict.ictbase.commons.HTableWithIndexesDriver;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.InternalScanner;
import org.apache.hadoop.hbase.regionserver.ScanType;
import org.apache.hadoop.hbase.regionserver.ScannerContext;
import org.apache.hadoop.hbase.regionserver.Store;
import org.apache.hadoop.hbase.regionserver.StoreFile;
import org.apache.hadoop.hbase.regionserver.StoreScanner;
import org.apache.hadoop.hbase.util.Bytes;

public class PhysicalDeletionInCompaction extends BasicIndexObserver
{
  static boolean enableDebugLogging = false;

  public InternalScanner preCompact(ObserverContext<RegionCoprocessorEnvironment> e, Store store, InternalScanner scanner, ScanType scanType)
    throws IOException
  {
    InternalScanner s = super.preCompact(e, store, scanner, scanType);
    if (scanner instanceof StoreScanner) {
      return new TTScannerWrapper((StoreScanner)scanner, this.dataTableWithIndexes);
    }
    String errMsg = "d is not storescanner!";
    LOG.error("TTDEBUG_ERR: " + errMsg);
    return s;
  }

  public void postCompact(ObserverContext<RegionCoprocessorEnvironment> c, Store store, StoreFile resultFile)
  {
    super.postCompact(c, store, resultFile);
    if (resultFile == null) {
      return;
    }
    if (enableDebugLogging)
      LOG.debug("TTDEBUG: resultFile is " + resultFile);
  }

  static class TTScannerWrapper
    implements InternalScanner
  {
    StoreScanner delegate;
    HTableWithIndexesDriver htablewIndexes;
    Set<String> indexed;

    public TTScannerWrapper(StoreScanner d, HTableWithIndexesDriver h)
      throws IOException
    {
      this.delegate = d;
      this.htablewIndexes = h;

      HTableDescriptor dataTableDesc = this.htablewIndexes.getTableDescriptor();
      this.indexed = new HashSet<String>();
      for (int index = 1; ; ++index) {
        String fullpathOfIndexedcolumnInDatatable = dataTableDesc.getValue(new StringBuilder().append("secondaryIndex$").append(index).toString());
        if (fullpathOfIndexedcolumnInDatatable == null) {
          return;
        }

        this.indexed.add(fullpathOfIndexedcolumnInDatatable);
      }
    }

    private void filterKVs(List<Cell> results)
      throws IOException
    {
      byte[] preBuffer = null;
      int preColumnFamilyOffset = 0;
      int preColumnFamilyLength = 0;
      int preColumnQualOffset = 0;
      int preColumnQualLength = 0;
      byte[] buffer = null;
      int columnFamilyOffset = 0;
      int columnFamilyLength = 0;
      int columnQualOffset = 0;
      int columnQualLength = 0;
      byte[] columnFamily = null;
      for (int i = 0; i < results.size(); ++i) {
        Cell kv = (Cell)results.get(i);
        buffer = kv.getRowArray();
        columnFamily =CellUtil.cloneFamily(kv);
        if (PhysicalDeletionInCompaction.enableDebugLogging) {
          LoggedObserver.LOG.debug(new StringBuilder().append("TTDEBUG: current KV=").append((kv == null) ? "null_kv" : kv.toString()).append("; htable==null:").append(this.htablewIndexes == null).toString());
        }
        if (preBuffer != null) {
          if (!(this.indexed.contains(new StringBuilder().append(Bytes.toString(columnFamily)).append("|").append(Bytes.toString(CellUtil.cloneQualifier(kv))).toString()))) {
            continue;
          }

          boolean repeatRow = Bytes.compareTo(buffer, columnFamilyOffset, columnFamilyLength, preBuffer, preColumnFamilyOffset, preColumnFamilyLength) == 0;
          repeatRow = (repeatRow) && (Bytes.compareTo(buffer, columnQualOffset, columnQualLength, preBuffer, preColumnQualOffset, preColumnQualLength) == 0);
          if (repeatRow)
          {
            results.remove(i);
            --i;
//            this.htablewIndexes.deleteFromIndex(columnFamily, CellUtil.cloneQualifier(kv),   CellUtil.cloneValue(kv), kv.getRow());
            this.htablewIndexes.deleteFromIndex(columnFamily, CellUtil.cloneQualifier(kv),   CellUtil.cloneValue(kv), CellUtil.cloneRow(kv));
          }
        }

        preBuffer = buffer;
        preColumnFamilyOffset = columnFamilyOffset;
        preColumnFamilyLength = columnFamilyLength;
        preColumnQualOffset = columnQualOffset;
        preColumnQualLength = columnQualLength;
      }
    }

    public void close() {
      this.delegate.close();
    }

    public boolean next(List<Cell> results) throws IOException {
      boolean ifDone = this.delegate.next(results);
      /************************************************************/
      for(int i =0;i<results.size();i++){
    	  System.out.println("**************:"+results.get(i).toString());
      }
      /************************************************************/
      filterKVs(results);
      return ifDone;
    }
//    public boolean next(List<Cell> results, int limit) throws IOException {
//      boolean ifDone = this.delegate.next(results, limit);
//      filterKVs(results);
//      return ifDone;
//    }

	public boolean next(List<Cell> results, ScannerContext scannerContext)
			throws IOException {
		boolean ifDone = this.delegate.next(results, scannerContext);
		 /************************************************************/
	      for(int i =0;i<results.size();i++){
	    	  System.out.println("**************:"+results.get(i).toString());
	      }
	      /************************************************************/
		filterKVs(results);
		return ifDone;
	}
  }
}