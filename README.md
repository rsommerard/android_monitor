UCN data collector for Android
==============================

JSON Data Formats
-----------------

Each data object has the following common fields:

```
{
  "data" : { ... },                                  // the collected data object
  "app_version_code" : 4,                            // data collector version code
  "app_version_name" : "0.4",                        // data collector version name
  "userlabel" : "unknown",                           // user given device label
  "hostname" : "android-e2e3a3bc2b0eb41d",           // device hostname
  "uid" : "d9ffb08c-3e6b-3767-8602-3a5b27ad66ef",    // unique device identifier
  "ts_event" : 1398175708736,                        // measurement event timestamp
  "ts_utc" : 1398175709974,                          // data record timestamp UTC
  "ts_local" : 1398175709000,                        // data record timestamp host timezone
  "tz" : "Europe/London",                            // host timezone
  "collection" : "app_data_usage",                   // data collection name
  "upload" : {                                       // server metadata
  	  "server_ts" : 1405220072672,               // upload time
	  "req_ip" : "82.26.241.172",                // uploading cli IP
	  "req_path" : "/ucntest"
  },
  "_id" : ObjectId("53c1f4e8c0f821c31d51401c")       // mongo object id
}
```

* app_data_usage [AppDataUsageCollector.java]:
```
{
   "process_list" : [
        {
          "uid" : 10084,
          "proc_uid_stat_tcp" : {     // from /proc/uid_stat/*/tcp_[snd|rcv]
             "recv" : 4008,
             "send" : 329944
          },
          "android_traffic_stats" : { // from android.net.TrafficStats
             "uid_udp_tx_pkts" : -1,
             "uid_rx_pkts" : 138,
             "uid_udp_rx_pkts" : -1,
             "uid_tcp_rx_bytes" : -1,
             "uid_tcp_rx_pkts" : -1,
             "uid_udp_tx_bytes" : -1,
             "uid_tx_bytes" : 344368,
             "uid_rx_bytes" : 11256,
             "uid_tcp_tx_pkts" : -1,
             "uid_udp_rx_bytes" : -1,
             "uid_tx_pkts" : 276,
             "uid_tcp_tx_bytes" : -1
          },
          "packages" : [
             {
               "package" : "fr.inria.ucn",
               "app_label" : "DataCollector"
             }
          ]
        },
	....
  ] // end process_list
}
```

* data_conn_state [MyPhoneStateListener.java]:
```
{
	"network_type_str" : "HSPA",
	"network_type" : 10,
	"state" : 0
}
```

* device_info [DeviceInfoCollector.java]:
```
{
	"build" : {
		"product" : "nakasi",
		"cpu_api" : "armeabi-v7a",
		"model" : "Nexus 7",
		"manufacturer" : "asus",
		"device" : "grouper",
		"brand" : "google",
		"display" : "KOT49H",
		"version_sdk" : 19,
		"version_release" : "4.4.2",
		"board" : "grouper"
	},
	"display" : {
		"density" : 1.3312500715255737,
		"width" : 800,
		"height" : 1205,
		"dpi" : 213
	}
}
```

* cell_location [MyPhoneStateListener]:
```
{
	"gsm" : {
		"cid" : 2252878,
		"lac" : 1021,
		"psc" : 383
	}
}
```

* network_state [NetworkStateCollector.java]:
```
{
	"is_airplane_mode" : false,
	"wifi_network" : {
		"ssid" : "OpenWrtTest",
		"bssid" : "e0:46:9a:4e:6c:2f",
		"mac" : "08:60:6e:9f:c4:dd",
		"rssi" : -20,
		"link_speed_units" : "Mbps",
		"link_speed" : 65,
		"ip" : "192.168.1.247"
	},
	"active_network" : {
		"detailed_state" : "CONNECTED",
		"state" : "CONNECTED",
		"type_name" : "WIFI",
		"subtype_name" : "",
		"type" : 1,
		"is_wifi" : true,
		"subtype" : 0
	},
	"net_interfaces" : [ 
		{
			"stats" : {
				"tx_errors" : 0,
				"tx_bytes" : 22205208,
				"tx_packets" : 63163,
				"rx_packets" : 218754,
				"tx_drop" : 0,
				"rx_errors" : 0,
				"rx_bytes" : 123472035,
				"rx_drop" : 114175
			},
			"name" : "wlan0"
		},
		...
	],
	"is_roaming" : false,
	"mobile_network" : {
		"network_operator_name" : "",
		"network_type_str" : "UNKNOWN [0]",
		"phone_type_str" : "None",
		"neigh_cells" : [ ],
		"sim_state" : 0,
		"network_type" : 0,
		"phone_type" : 0,
		"network_operator" : "",
		"network_country" : "",
		"call_state" : 0,
		"data_activity" : 0
	},
	"ip_addr_show" : [
		{
			"ipv6" : {
				"mask" : "64",
				"scope" : "link",
				"ip" : "fe80::a60:6eff:fe9f:c4dd"
			},
			"mtu" : 1500,
			"ipv4" : {
				"mask" : "24",
				"scope" : "global",
				"brd" : "192.168.1.255",
				"ip" : "192.168.1.247"
			},
			"flags" : [
				"BROADCAST",
				"MULTICAST",
				"UP",
				"LOWER_UP"
			],
			"stats" : {
				"tx_errors" : 0,
				"tx_bytes" : 22205208,
				"tx_packets" : 63163,
				"rx_packets" : 218754,
				"tx_drop" : 0,
				"rx_errors" : 0,
				"rx_bytes" : 123472035,
				"rx_drop" : 114175
			},
			"name" : "wlan0",
			"state" : "UP",
			"qdisc" : "pfifo_fast",
			"mac" : "08:60:6e:9f:c4:dd"
		},
		...
	],
	"is_connected" : true,
	"on_network_state_change" : false
}
```

* running_apps [RunningAppsCollector.java]:
```
{
	"runningAppProcesses" : [
		{
			"proc_uid" : 10048,
			"proc_name" : "UCNDataCollector"
            "packages" : [
	             {
	               "package" : "fr.inria.ucn",
	               "app_label" : "UCNDataCollector"
	             }
	        ]
		},
		...
	],
	"runningTasks" : [
		{
			"task_num_activities" : 1,
			"task_id" : 43,
			"task_foreground" : true,
			"task_num_running" : 1,
			"task_app_label" : "UCNDataCollector",
			"task_top_package_name" : "fr.inria.ucn",
			"task_top_class_name" : "fr.inria.ucn.ui.MainActivity"
		},
		...
	]		
}
```

* system_state [SysStateCollector.java]:
```
{
	"uptime" : {
		"uptime" : 264538.15625,
		"idle_time" : 1028228.3125
	},
	"cpu" : {
		"total" : 1.5228426395939085,
		"user" : 0,
		"system" : 1.5228426395939085
	},
	"screen_on" : true,
	"memory" : {
		"total" : 1022046208,
		"is_low" : false,
		"available" : 451600384
	},
	"on_screen_state_change" : false,
	"loadavg" : {
		"1_min_average" : 0.5,
		"5_min_average" : 0.20999999344348907,
		"total_tasks" : 721,
		"active_tasks" : 3,
		"15_min_average" : 0.12999999523162842
	},
	"battery" : {
		"is_charging" : true,
		"scale" : 100,
		"level" : 100,
		"pct" : 100,
		"usb_charge" : true,
		"ac_charge" : false
	}
}
```

* llama_location [LlamaCollector.java]:
```
{
	"locations" : [
		{
			"wifi_networks" : [ ],
			"cells" : [ ],
			"name" : "Home"
		},
		{
			"wifi_networks" : [
				"W:LINCS",
				"W:LINCS",
				"W:LINCS",
				"W:LINCS"
			],
			"cells" : [ ],
			"name" : "Work"
		}
	],
	"source_file" : "/storage/emulated/0/Llama/Llama_Areas.txt",
	"provider" : "Llama"
}
```
