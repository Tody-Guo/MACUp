package com.tware.macup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MACUpActivity extends Activity {
    /** Called when the activity is first created. */
	final public String TAG = "TestLOG";
	private static String RKPATH = "/dev/rknand_sys_storage";
	private EditText ipAddr;
	private EditText port;
	private EditText mac;
	private EditText sn;
	private Button upMac;
	private WifiManager mWifi;
	private String WifiMac;
	private TextView ret;
	private String serialnumber;
	private Boolean snOk = true;
	private String TESTLOG;  // 2012-03-05 added
	private String LOGMSG;   // 2012-03-05 added
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.tware.macup.R.layout.main);
        
        ipAddr = (EditText)findViewById(com.tware.macup.R.id.E_address);
        port	= (EditText)findViewById(com.tware.macup.R.id.E_port);
        mac	= (EditText)findViewById(com.tware.macup.R.id.E_mac);
        upMac	= (Button)findViewById(com.tware.macup.R.id.B_macup);
        ret	= (TextView)findViewById(com.tware.macup.R.id.V_ret);
        sn = (EditText)findViewById(com.tware.macup.R.id.E_sn);

        /*
         *  Read Serial number from JNI. 
         */
        if (isRk30())
        {
        	Log.e(TAG, "Read SN for Rk30");
        	sn.setText(rkReadSn().toUpperCase(Locale.US));
        }else{
        	Log.e(TAG, "Read SN for Nvidia or Amlogic");	
        	sn.setText(ReadSerial().toUpperCase(Locale.US));
        }
	
        serialnumber = sn.getText().toString().trim();

        if (serialnumber.contains("FAIL") || serialnumber.equals(""))
        {
        	snOk = false;
        	sn.setTextColor(Color.RED);
        	sn.setText("N/A");
        }
        
        serialnumber = sn.getText().toString().trim(); // 2012-03-05 added
        
        TESTLOG = this.getIntent().getStringExtra("LOG"); // 2012-03-05 added
        
	mWifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);

        if (!mWifi.isWifiEnabled())
        {
        	mWifi.setWifiEnabled(true);
        }
        
        if (serialnumber.length() < 17 && !sn.getText().toString().equals("N/A")) // 2012-03-28 added
        {
        	new AlertDialog.Builder(this).setIcon(R.drawable.mac)
        		.setTitle("错误")
        		.setMessage("机台序列号位数不正确，请重流前测！")
        		.setPositiveButton("Ok", null)
        		.show();
        	sn.setTextColor(Color.RED);
        	upMac.setEnabled(false);
        	return ; 
        }
        
        upMac.setOnClickListener(new OnClickListener(){
        	public void onClick(View v)
        	{
                WifiInfo wifiInfo = mWifi.getConnectionInfo();
                
                if((WifiMac = wifiInfo.getMacAddress())== null)
                {
                	new AlertDialog.Builder(MACUpActivity.this)
                		.setTitle("Warning")
                		.setMessage("No MAC Address Found!")
                		.setPositiveButton("", null);
                	return ;
                }
                WifiMac = WifiMac.toUpperCase(Locale.US);
                mac.setText(WifiMac.toUpperCase(Locale.US));

                LOGMSG = "NOSN|" + WifiMac; // 2012-03-05 added

                if (snOk)
                {
                	LOGMSG = serialnumber + '|' + WifiMac;
                }
                
                
                if(TESTLOG != null) // 2012-03-05 added new log format, before info.
                {
                	if(snOk)
                	{
                		LOGMSG = serialnumber + TESTLOG;
                	}else{
                		LOGMSG = "NOSN" + TESTLOG;
                	}
                }
                
                try {
                	// 2012-03-05: change sendMac(WifiMac+"\0")) to sendMac(LOGMSG+"\0") 
                	if(sendMac(LOGMSG+"\0"))  //Sending MAC Address to Server
					{
		                ret.setTextColor(Color.GREEN);
		                upMac.setTextColor(Color.GREEN);
		                ret.append("\n :o-----> Upload Ok");
		                upMac.setEnabled(false);						
					}
					else
					{
		                ret.setTextColor(Color.RED);
		                upMac.setTextColor(Color.RED);
		                ret.append("\n :<-----> Upload fail");
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
	                ret.setTextColor(Color.RED);
	                upMac.setTextColor(Color.RED);
	                ret.append("\n :n-----> Upload Fail");

					return ;
				} catch (UnknownHostException e) {
					e.printStackTrace();
	                ret.setTextColor(Color.RED);
	                upMac.setTextColor(Color.RED);
	                ret.append("\n :u-----> Upload Fail");
					return ;
				} catch (IOException e) {
					e.printStackTrace();
	                ret.setTextColor(Color.RED);
	                upMac.setTextColor(Color.RED);
	                ret.append("\n :i-----> Upload Fail");
					return ;
				}
        	}
        });
    }
    
    boolean sendMac(String WifiMac) throws NumberFormatException, UnknownHostException, IOException
    {
    	Socket sock = new Socket(ipAddr.getText().toString(),
    							Integer.parseInt(port.getText().toString()));
    	
    	Log.e(TAG, "TESTLOG: "+WifiMac);
    	
        OutputStream sout = sock.getOutputStream();
        InputStream sin = sock.getInputStream();
        
        byte obuf [] = WifiMac.getBytes();
        obuf[obuf.length-1] ='\0';
        sout.write(obuf);
        sout.flush(); // flush buffer and send to server right now

        byte ibuf [] = new byte[512];
        int len = sin.read(ibuf);
        String is = new String(ibuf, 0, len);
        Log.d(TAG, is);
        if (is.contains("MAC_ERR"))
        {
        	ret.setTextColor(Color.RED);
        	ret.append("\n :s-----> Server return MAC_ERR");
            sout.close(); // socket output close
            sin.close(); //socket input close
            sock.close(); // socket close, both read and write
        	return false;
        }else if(is.contains("MAC_REJECT"))
        {
        	ret.setTextColor(Color.RED);
        	ret.append("\n :s-----> Server return MAC_Reject");
            sout.close(); // socket output close
            sin.close(); //socket input close
            sock.close(); // socket close, both read and write
        	return false;        	
        }else{
        	ret.setTextColor(Color.GREEN);
        	ret.append("\n :s-----> Server return MAC_OK");        	        	
        }

        sout.close(); // socket output close
        sin.close(); //socket input close
        sock.close(); // socket close, both read and write
        return true;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 0, 0, "Quit");
        menu.add(0, 1, 1, "About");
        menu.add(0, 2, 2, "Network ");
        
        return true;
    }
    
    public static boolean fileExists(String path)
    {
    	File f = new File(path);
    	if(f.exists())
    		return true;
    	else	
    		return false;
    }
    
    public static boolean isRk30()
    {
    	return fileExists(RKPATH);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch(item.getItemId())
        {
    		case 0:
    			Log.d(TAG, "Logout App.");
    			finish();
    			return true;
    			
    		case 1:
    			Log.d(TAG, "Reading About");
    			AlertDialog.Builder builder = new AlertDialog.Builder(MACUpActivity.this);	
    			try {
    				builder.setTitle("About")
    					.setIcon(com.tware.macup.R.drawable.mac)
    					.setMessage("Test Logs Uploader "+ 
    							this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName
    							+"\n\n(c) Tody 2012, T-ware Inc.")
    					.setPositiveButton("Ok", null).show();
    			} catch (NameNotFoundException e) {
    				e.printStackTrace();
    			}
    			return true;

    		case 2:
    			Log.d(TAG, "Setting wireless");
    			startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    			return true;

        }
        return false;
    }    
    
    static {
    	System.loadLibrary("readsn");
    }
    
    static public native String ReadSerial();
    static public native String rkReadSn();
    
    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	Process.killProcess(Process.myPid());
    }

}