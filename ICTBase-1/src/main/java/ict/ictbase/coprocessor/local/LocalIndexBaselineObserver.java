package ict.ictbase.coprocessor.local;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.Region;
import org.apache.hadoop.hbase.regionserver.wal.WALEdit;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;

public class LocalIndexBaselineObserver extends LocalIndexBasicObserver{
	
	Region region = null;
	HRegionInfo regionInfo = null;// e.getEnvironment().getRegionInfo();
	String regionStartKey = null;//Bytes.toString(regionInfo.getStartKey());
	@Override
    public void prePut(final ObserverContext<RegionCoprocessorEnvironment> e, final Put put, final WALEdit edit, final Durability durability) throws IOException {
		super.prePut(e, put, edit, durability);
		long now = EnvironmentEdgeManager.currentTime();
		byte[] byteNow = Bytes.toBytes(now);
		Map<byte[], List<Cell>> familyMap = put.getFamilyCellMap();
		for (Entry<byte[], List<Cell>> entry : familyMap.entrySet()) {
			List<Cell> cells = entry.getValue();
			for (Cell cell : cells) {
				CellUtil.updateLatestStamp(cell, byteNow, 0);
			}
		}
		put.setAttribute("put_time_version", Bytes.toBytes(now));
		/************************************************************/
//		if(put.getAttribute("index_put")==null){
//			System.out.println("a put: "
//					+ Bytes.toLong(put.getAttribute("put_time_version")));
//		}
		/************************************************************/
		
		/**************for time break down test********************/
//		if(put.getAttribute("index_put")==null){
//			System.out.println("start put base table: "+System.nanoTime());
//		}
		
		
		/**************for time break down test********************/
    }
	
	public void postPut(final ObserverContext<RegionCoprocessorEnvironment> e,
			final Put put, final WALEdit edit, final Durability durability)
			throws IOException {
//		region = e.getEnvironment().getRegion();
//		regionInfo =  e.getEnvironment().getRegionInfo();
//		regionStartKey = Bytes.toString(regionInfo.getStartKey());
		
		/**************for time break down test********************/
		
		if(put.getAttribute("index_put")==null){
			System.out.println("end put base table: "+System.nanoTime());
			
			region = e.getEnvironment().getRegion();
			regionInfo =  e.getEnvironment().getRegionInfo();
			regionStartKey = Bytes.toString(regionInfo.getStartKey());
			
			dataTableWithLocalIndexes.readBaseAndDeleteOld(put,regionStartKey,region);
			dataTableWithLocalIndexes.insertNewToIndexes(put,regionStartKey,region);
		}
	   /**************for time break down test********************/
		
		
//		synchronized(this){
//			dataTableWithLocalIndexes.readBaseAndDeleteOld(put,regionStartKey,region);
//			dataTableWithLocalIndexes.insertNewToIndexes(put,regionStartKey,region);
//		}
	}
}
