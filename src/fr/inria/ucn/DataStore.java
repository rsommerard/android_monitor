/*******************************************************************************
 * Copyright (C) 2014 MUSE team Inria Paris - Rocquencourt
 * 
 * This file is part of UCNDataCollector.
 * 
 * UCNDataCollector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * UCNDataCollector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero Public License for more details.
 * 
 * You should have received a copy of the GNU Affero Public License
 * along with UCNDataCollector.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package fr.inria.ucn;

import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Collector data store (raw measurement data + misc app configuration & status info).
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class DataStore {
	private SQLiteDatabase database;
	private MySQLiteOpenHelper dbHelper;
	private boolean readonly;

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "ucndata.db";
	
	private static final String DATA_TABLE_CREATE = "CREATE TABLE data (id INTEGER primary key autoincrement, json TEXT);";
	private static final String KV_TABLE_CREATE = "CREATE TABLE kv (key TEXT, value TEXT);";
	private static final String SELECT_DATA = "SELECT * FROM data";
	private static final String SELECT_KV = "SELECT value FROM kv WHERE key=?";

	/** Open helper */
	private class MySQLiteOpenHelper extends SQLiteOpenHelper {

		public MySQLiteOpenHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		/* (non-Javadoc)
		 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
		 */
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATA_TABLE_CREATE);
			db.execSQL(KV_TABLE_CREATE);
		}

		/* (non-Javadoc)
		 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(Constants.LOGTAG,
					"Upgrading database [" + DATABASE_NAME + "] from version " + oldVersion + " to " + newVersion);
			db.execSQL("DROP TABLE IF EXISTS data");
			db.execSQL("DROP TABLE IF EXISTS kv");
			onCreate(db);
		}

	}	

	/**
	 * 
	 * @param context
	 */
	public DataStore(Context context) {
		dbHelper = new MySQLiteOpenHelper(context);
	}

	/**
	 * 
	 * @throws SQLException
	 */
	public void open(boolean readonly) throws SQLException {
		this.readonly = readonly;
		if (readonly) {
			database = dbHelper.getReadableDatabase();
		} else {
			database = dbHelper.getWritableDatabase();
		}
	}

	/**
	 * 
	 */
	public void close() {
		dbHelper.close();
	}

	/**
	 * Add a key-value pair. Overwrites an existing value with the same key.
	 * @param key
	 * @param value
	 */
	public void addKeyValue(String key, String value) throws CollectorException {
		if (readonly)
			throw new CollectorException("Tried to write to a readonly datastore handle!");
		
		if (key!=null) {
			String sql = null;
			if (getKeyValue(key)!=null) {
				sql = "UPDATE kv SET value='"+value+"' WHERE key='"+key+"';";			
			} else {
				sql = "INSERT INTO kv (key, value) VALUES ('"+key+"', '"+value+"');";
			}
			database.execSQL(sql);
		}
	}

	/**
	 * @param key key to read
	 * @return value
	 */
	public String getKeyValue(String key) {
		String res = null;
		if (key!=null) {
			Cursor c = database.rawQuery(SELECT_KV, new String[] {key});
			if (c.moveToFirst() && !c.isNull(0)) {
				res = c.getString(0);
			}
			c.close();
		}
		return res;
	}
	
	/**
	 * Add a new data item.
	 * @param ts
	 * @param json
	 */
	public void addData(String json) {
		if (json!=null) {
			String sql = "INSERT INTO data (json) VALUES ('"+json+"');";
			database.execSQL(sql);		
		}
	}

	/**
	 * Get non-uploaded data items.
	 * @param limit max number of entries to return
	 * @return Map of id -> json
	 */
	@SuppressLint("UseSparseArrays")
	public Map<Integer,String> getData(int limit) {
		Map<Integer,String> res = new HashMap<Integer, String>();
		String q = SELECT_DATA;
		if (limit>0)
			q += " LIMIT " + limit;
		Cursor c = database.rawQuery(q, null);
		while (c.moveToNext()) {
			res.put(c.getInt(0), c.getString(1));
		}
		c.close();
		return res;
	}
	
	/**
	 * 
	 * @param idx
	 */
	public void removeData(int idx) {
		if (idx>=0) {
			String sql = "DELETE FROM data WHERE id="+idx+";";
			database.execSQL(sql);
		}
	}
}
