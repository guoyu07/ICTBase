package ict.ictbase.commons.global;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.HTableDescriptor;

import java.io.IOException;
import java.util.List;

import ict.ictbase.util.HIndexConstantsAndUtils;

public class GlobalHTableUpdateIndexByPut extends GlobalHTableWithIndexesDriver {
	public GlobalHTableUpdateIndexByPut(Configuration conf, byte[] tableName) throws IOException {
        super(conf, tableName);
    }

    final static private int INSERT_INDEX = 0;
    final static private int READ_BASE = 1;
    final static private int DELETE_INDEX = 2;

    /**
        @parameter, if DELETE_INDEX, readResult4Delete is not null, otherwise, null.
        @return, if READ_BASE, return read result; otherwise, null.
    */
    private Result internalPrimitivePerPut(Put put, int mode, Result readResult4Delete) throws IOException {
        HTableDescriptor dataTableDesc = null; 
        try {
            dataTableDesc = getTableDescriptor();
        } catch (IOException e1) {
            throw new RuntimeException("TTERROR" + (errorIndex++) + "_DETAIL: " + e1.getMessage());
        }
        byte[] dataKey = put.getRow();
        Get get = null;
        if (mode == READ_BASE) {
             get = new Get(dataKey);
        }
        for (int index = 1; ; index++) {
            String fullpathOfIndexedcolumnInDatatable = dataTableDesc.getValue(HIndexConstantsAndUtils.INDEX_INDICATOR + index);
            if(fullpathOfIndexedcolumnInDatatable == null){
                //no (further) index column, stop at current index
                break;
            } else {
                String[] datatableColumnPath = fullpathOfIndexedcolumnInDatatable.split("\\|");
                byte[] indexedColumnFamily = Bytes.toBytes(datatableColumnPath[0]);
                byte[] indexedColumnName = Bytes.toBytes(datatableColumnPath[1]); 
                byte[] dataValuePerColumn = getColumnValue(put, indexedColumnFamily, indexedColumnName);
                if(dataValuePerColumn != null){
                    if(mode == INSERT_INDEX){
                        //put new to index
                    	System.out.println("start put index table: "+System.nanoTime());
                        putToIndex(indexedColumnFamily, indexedColumnName, dataValuePerColumn, dataKey);
                        System.out.println("end put index table: "+System.nanoTime());
                        
                    } else if (mode == READ_BASE) {
                        //read base 
                        //TOREMOVE need specify timestamp to guarantee get old values.
                    	long maxTs = Bytes.toLong(put.getAttribute("put_time_version"));
                    	get.setTimeRange(0,maxTs);
                    	get.setMaxVersions();
                        get.addColumn(indexedColumnFamily, indexedColumnName);
                    } else { // DELETE_INDEX
                        //delete old from index
                        Result readResultOld = readResult4Delete;
                        List<Cell> list = readResultOld.listCells();
                        if(list ==null){
                        	break;
                        }
                        for(Cell cell : list){
                        	byte[] oldDataValuePerColumn  = CellUtil.cloneValue(cell);
//                        	System.out.println("&&&&&&&&&&&&&&&&&&&& oldDataValuePerColumn \t+"+Bytes.toString(oldDataValuePerColumn));
                        	long ts = cell.getTimestamp();
                        	 System.out.println("start delete index table: "+System.nanoTime());
                        	boolean isDelete = deleteFromIndex(indexedColumnFamily, indexedColumnName, oldDataValuePerColumn, dataKey);
                        	 System.out.println("end delete index table: "+System.nanoTime());
                        	
//                        	if(isDelete){
////                        		System.out.println("&&&&&&&&&&&&&&&&&&&& delete true");
//                        		deleteFromBaseTable(dataKey,ts);
//                        	}else{
////                        		System.out.println("&&&&&&&&&&&&&&&&&&&& delete false");
//                        	}
                            break;// only needs to remove the first value in index table
                        }
                        
                    }
                } else {
                    //the indexed column (family) is not associated with the put, to continue.
                    continue;
                }
            }
        }
        if (mode == READ_BASE) {
//        	System.out.println("start read base table: "+System.nanoTime());
             Result readResultOld = this.get(get);
//             System.out.println("end read base table: "+System.nanoTime());
             return readResultOld;
        } else {
             return null;
        }
    }

    public void insertNewToIndexes(Put put) throws IOException {
    	
        internalPrimitivePerPut(put, INSERT_INDEX, null);
        
//        System.out.println("b put: "+Bytes.toLong(put.getAttribute("put_time_version")));
    }

    public void readBaseAndDeleteOld(Put put) throws IOException {
    	System.out.println("start read base table: "+System.nanoTime());
        Result readBaseResult = internalPrimitivePerPut(put, READ_BASE, null);
        System.out.println("end read base table: "+System.nanoTime());
       
        
        internalPrimitivePerPut(put, DELETE_INDEX, readBaseResult);
       
//        System.out.println("b put: "+Bytes.toLong(put.getAttribute("put_time_version")));
    }

//TOREMOVE does it belong to HTableWithIndexesDriver?
//it gets one and only one version.
    protected byte[] getColumnValue(final Put put, byte[] columnFamily, byte[] columnName){
        if(!put.has(columnFamily, columnName)){
            return null;
        }

        List<Cell> values = put.get(columnFamily, columnName);
        if (values == null || values.isEmpty()) {
            throw new RuntimeException("TTERROR_" + (errorIndex++) + ": " + "empty value lists while put.has() returns true!");
        }

        //should be one element in values, since column qualifier is an exact name, matching one column; also one version of value is expected.
        if (values.size() != 1) {
            throw new RuntimeException("TTERROR_" + (errorIndex++) + ": " + "multiple versions of values or multiple columns by qualier in put()!");
        }

        //TOREMOVE to get timestamp, refer to old project code.
        Cell cur = values.get(0);
        byte[] value = CellUtil.cloneValue(cur);
        return value;
    }
}
