package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {

	static int SERVER_PORT = 10000;
	static int MYPORT;
	static HashAvd next1, next2, curr, prev;
	static TreeSet<HashAvd> avds = new TreeSet<HashAvd>();
	static File path;
	HashSet<String> keyOrder;

	static class HashAvd implements Comparable<HashAvd>
	{
		String hashName;
		int portNumber;
		HashAvd(String name, int port)
		{
			hashName = name;
			portNumber = port;
		}
		@Override
		public int compareTo(HashAvd another) {
			return hashName.compareTo(another.hashName);
		}
	}

	public static void setNext2()
	{
		if(avds.higher(next1) == null)
			next2 = avds.first();
		else
			next2 = avds.higher(next1);
	}

	public static HashAvd getPrev(HashAvd avd)
	{
		HashAvd previous;
		if(avds.lower(avd) == null)
			previous = avds.last();
		else
			previous = avds.lower(avd);
		return previous;
	}

	public static HashAvd getNext(HashAvd avd)
	{
		HashAvd next;
		if(avds.higher(avd) == null)
			next = avds.first();
		else
			next = avds.higher(avd);

		return next;
	}

	public static void setPrevCurr()
	{
		if(avds.lower(curr) == null)
			prev = avds.last();
		else
			prev = avds.lower(curr);

		if(avds.higher(curr) == null)
			next1 = avds.first();
		else
			next1 = avds.higher(curr);
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		/*File path = SimpleDynamoActivity.context.getFilesDir();  // check
		File file = new File(path,"data.properties");*/
		File file = new File(path,"data.properties");
		if(!file.exists())
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		Properties prop = new Properties();
		InputStream is = null;
		String key = selection;

		try {
			synchronized (this) {
				is = new FileInputStream(file);
				prop.load(is);
				if (key.equals("*") || key.equals("@")) {
					for (Object k : prop.keySet()) {
						String key1 = (String) k;
						prop.remove(key1);
						OutputStream out = new FileOutputStream(file);
						prop.store(out, null);
					}
				}
				else
				{
					boolean thisAvd = InsertIntoThisAvd(key, curr, getPrev(curr));
					if (thisAvd) {
						Log.v("DELETE SELF", key);
						prop.remove(key);
						OutputStream out = new FileOutputStream(file);
						prop.store(out, null);
						String msg = "DELETE\n"+key;
						HashAvd n1 = getNext(curr);
						HashAvd n2 = getNext(n1);
						Log.v("DELETE", "Delete request to replicas "+n1.portNumber+" "+ n2.portNumber);
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(n1.portNumber));
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(n2.portNumber));
					}
					 else {
							Log.v("INFO", "Delegating delete " + key);
							for (HashAvd avd : avds) {
								if (InsertIntoThisAvd(key, avd, getPrev(avd))) {
									String msg = "DELETE DELEGATE\n"+key;
									Log.v("DELETE DELEGATE"," "+key+" completed");
									try {
										Socket sock = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), avd.portNumber);
										PrintWriter writer = new PrintWriter(sock.getOutputStream(), true);
										writer.println(msg);
										writer.flush();
										Scanner in = new Scanner(sock.getInputStream()); // used for detecting socket failure;
										String dummy_message = in.nextLine();
										Log.v("DELETE DELEGATION","SUCCESS. Dummy value received "+dummy_message+" for key"+key);
									}
									catch(Exception e)
									{
										Log.e("DELETE DELGATION","Not possible due to node failure "+e);
										Log.v("DELETE Direct", "SENDING TO replicas now "+key);
										HashAvd next11 = getNext(avd);
										HashAvd next22 = getNext(next11);
										msg = "DELETE\n"+key;
										Log.v("DELETE", "Direct delete for failed node "+avd.portNumber+" :- "+next11.portNumber+" "+next22.portNumber+" key "+key);
										new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(next11.portNumber));
										new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(next22.portNumber));
									}
									break;
								}
							}
						}
				}
			}
			return 1;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		Log.v("query", selection);
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onCreate() {
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
		MYPORT = Integer.parseInt(myPort);
		Log.v("ONCREATE","Started Avd "+MYPORT);
		path = getContext().getFilesDir();
		try
		{
			avds.add(new HashAvd(genHash("5554"), 11108));
			avds.add(new HashAvd(genHash("5556"), 11112));
			avds.add(new HashAvd(genHash("5558"), 11116));
			avds.add(new HashAvd(genHash("5560"), 11120));
			avds.add(new HashAvd(genHash("5562"), 11124));
			curr = new HashAvd(genHash(portStr), Integer.parseInt(myPort));
			avds.add(curr);
			setPrevCurr();
			setNext2();
			String msg = "QUERY_ALL";
			new OnCreateTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(curr.portNumber)); //check if port is required here
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT,150);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		}
		catch(Exception e)
		{
			Log.e("ERROR"," onCreate() "+e.getMessage());
		}
		return false;
	}

	public boolean InsertIntoThisAvd(String key, HashAvd avd, HashAvd prev)
	{
		boolean thisAvd = false;
		try {
			if (genHash(key).compareTo(avd.hashName) < 0 && genHash(key).compareTo(prev.hashName) > 0)
				thisAvd = true;

			if (avd.hashName.compareTo(prev.hashName) < 0 && (genHash(key).compareTo(avd.hashName) < 0 || genHash(key).compareTo(prev.hashName) > 0))
				thisAvd = true;
		}
		catch(Exception e)
		{
			Log.e("ERROR", e.getMessage());
		}
		Log.v("INSERT " + thisAvd+" belong", "key "+key+" avd"+avd.portNumber);
		return thisAvd;
	}

	public void insertToFile(String key, String value)
	{
		try {
			synchronized (this) {

				Log.v("FILE","$$$ "+getContext().getFilesDir().toString());
		  	    File file = new File(path,"data.properties");
				if (!file.exists())
					file.createNewFile();

				keyOrder.add(key);

				Properties prop = new Properties();
				InputStream is;

				is = new FileInputStream(file);
				prop.load(is);
				prop.setProperty(key, value);
				OutputStream out = new FileOutputStream(file);
				prop.store(out, "added :|");
			}
		} catch(NullPointerException e)
		{
			Log.e("FILE WRITE","NULL POINTER "+e);
		}
		 catch(Exception e)
		{
			Log.e("FILE WRITE", "Exception"+e);
		}

	}

	public String getValueFromFile(String key)
	{
		String value="";
		try {
			/*File path = SimpleDynamoActivity.context.getFilesDir();
			File file = new File(path, "data.properties");*/
			File file = new File(path,"data.properties");
			if (!file.exists())
				file.createNewFile();
			Properties prop = new Properties();
			InputStream is;
			synchronized (this) {
				is = new FileInputStream(file);
				prop.load(is);
				value = prop.getProperty(key).split(" ")[0];
				Log.v("INFO", "Returning value " + value);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return value;
	}

	@Override
	public  Uri insert(Uri uri, ContentValues values) {

		String key = values.getAsString("key");
		Log.v("INSERT REQUEST","Insert for key "+ key);
		String value = values.getAsString("value");
		String msg ="";
		boolean thisAvd;
		thisAvd = InsertIntoThisAvd(key, curr, getPrev(curr));

		if (thisAvd) {
			//          Log.v("INSERT", "own");
			value = value +" "+String.valueOf(curr.portNumber);
			insertToFile(key, value);
			Log.v("INSERT SELF", key);
			Log.v("INSERT","Replicas of "+MYPORT+" are "+next1.portNumber+" "+next2.portNumber +" for key "+key);
			msg = "Insert\n"+key+"\n"+value;
			Log.v("INSERT", "SENDING TO REPLICAS");
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(next1.portNumber));
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(next2.portNumber));

		} else {

			Log.v("INFO","Delegating Insert "+key);
			for(HashAvd avd : avds)
			{
				if(InsertIntoThisAvd(key, avd, getPrev(avd)))
				{
					Log.v("INSERT DELEGATE","Delegating key"+key+" to "+avd.portNumber);
					String val = value + " "+String.valueOf(avd.portNumber);
					msg = "Insert Delegated\n"+key+"\n"+val;
					Log.v("INSERT DELEGATE", "sending msg "+msg );
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(avd.portNumber));
					Log.v("INSERT DELEGATE","Delegation for "+key+" completed");
					try {
						Socket sock = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), avd.portNumber);
						PrintWriter writer = new PrintWriter(sock.getOutputStream(), true);
						writer.println(msg);
						writer.flush();
						Scanner in = new Scanner(sock.getInputStream()); // used for detecting socket failure;
						String dummy_message = in.nextLine();

						Log.v("INSERT DELEGATION","SUCCESS. Dummy value received "+dummy_message+" for key"+key);
					}
					catch(Exception e)
					{
						//avdStatusMap.put(avd.portNumber,false);
						Log.e("INSERT DELGATION","could not delegate..mostly because of socket failure "+e);
						Log.v("INSERT", "SENDING TO replicas now "+key);
						HashAvd next11 = getNext(avd);
						HashAvd next22 = getNext(next11);
						val = value + " "+String.valueOf(avd.portNumber);
						msg = "Insert\n"+key+"\n"+val;
						Log.v("INSERT", "SENDING TO REPLICAS OF FAILED NODE "+avd.portNumber+" :- "+next11.portNumber+" "+next22.portNumber+" key "+key);
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(next11.portNumber));
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, String.valueOf(next22.portNumber));
					}
					break;
				}
			}

		}

		return null;
	}

	@Override
	public  Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		Log.v("QUERY","For key "+selection);
		MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
		/*File path = SimpleDynamoActivity.context.getFilesDir();  // check
		File file = new File(path, "data.properties");*/
		File file = new File(path,"data.properties");
		if (!file.exists())
			try {
				file.createNewFile();
			} catch (IOException e) {
				Log.e("QUERY", "file could not be created");
			}
		Properties prop = new Properties();
		InputStream is = null;
		String key = selection;
		Log.v("QUERY", "Key is "+key);
		try {
			is = new FileInputStream(file);
			prop.load(is);
			if (key.equals("@")) {
				for (Object k : prop.keySet()) {
					String key1 = (String) k;
					String val1 = prop.getProperty(key1).split(" ")[0];
					cursor.addRow(new String[]{key1, val1});
					Log.v("INFO @", key1 + "  " + val1);
				}
			} else if (selection.equals("*")) {
				for (Object k : prop.keySet()) {
					String key1 = (String) k;
					String val1 = prop.getProperty(key1).split(" ")[0];
					cursor.addRow(new String[]{key1, val1});
					Log.v("STORED", key1 + "  " + val1);
				}

				Log.v(String.valueOf(MYPORT), "SELECTION key " + key);
				String value = "";
				for (HashAvd avd : avds) {
					if (avd.portNumber == MYPORT)
						continue;
					else {
						String msg = "Query *";  // problems might occur during failure
						Socket sock = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), avd.portNumber);
						PrintWriter writer = new PrintWriter(sock.getOutputStream(), true);
						writer.println(msg);
						writer.flush();
						Scanner in = new Scanner(sock.getInputStream());
						try {
							String key1 = in.nextLine();
							if (key1.equals("empty"))
								continue;
							String value1 = in.nextLine();
							value1 = value1.split(" ")[0];
							cursor.addRow(new String[]{key1, value1});
							while (in.hasNext()) {
								key1 = in.nextLine();
								value1 = in.nextLine();
								value1 = value1.split(" ")[0];
								cursor.addRow(new String[]{key1, value1});
							}
						}catch(Exception e)
						{
							Log.e("QUERY", "exception generated");
						}
					}
				}
			}
			else
			{
				String val = null;
				if(InsertIntoThisAvd(key,curr,getPrev(curr)))
					val = prop.getProperty(key); // **this will have port number also appended

				if(val == null)
				{
					for(HashAvd avd : avds)
					{
						if(InsertIntoThisAvd(key, avd, getPrev(avd)))
						{
							Log.v("QUERY DELEGATE", " SEnding key "+key+" to "+avd.portNumber);
							String message = "Query Delegated\n"+key;
							Socket sock1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), avd.portNumber);

							PrintWriter writer1 = new PrintWriter(sock1.getOutputStream(), true);
							writer1.println(message);
							writer1.flush();
							Scanner in = new Scanner(sock1.getInputStream());

							try
							{
								val = in.nextLine();
							}
							catch(Exception e)
							{
								sock1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), getNext(avd).portNumber);
								writer1 = new PrintWriter(sock1.getOutputStream(), true);
								writer1.println(message);
								writer1.flush();
								in = new Scanner(sock1.getInputStream());
								if(in.hasNext()) {
									val = in.nextLine();
								}
								else {
									val = getValueFromFile(key); // hacky fix if there was a slight delay in insertion..might not be required now
									Log.e("QUERY","XXXX  THIS WAS AN ERROR. COULDN'T GET VALUE FOR KEY "+key+" using val "+val);
								}

							}

							in.close();
							sock1.close();
							break;
						}
					}
				}
				else
				{
					val = val.split(" ")[0];
				}

				cursor.addRow(new String[]{key,val});
				Log.v("QUERY RESULT", "Query returning "+ val+" for key " + key);
			}
			return cursor;

		} catch (UnknownHostException e) {
			Log.e("QUERY", "UnknownHostException Exception for key "+ key+" "+e);
		} catch (FileNotFoundException e) {
			Log.e("QUERY", "FileNotFoundException for key "+ key+" "+e);
		} catch (IOException e) {
			Log.e("QUERY", "IOException for key "+ key+" "+e);
		} catch (Exception e){
			Log.e("QUERY", "Exception " + key+" "+e);
		}
		return null;
	}

		@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

    private static String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

	public static Uri getUri()
	{
		String uriii = "edu.buffalo.cse.cse486586.simpledynamo.provider";
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(uriii);
		uriBuilder.scheme("content");
		Uri uri =  uriBuilder.build();
		return uri;
	}

	private class ClientTask extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(String... strings) {
			String msg = strings[0];
			String port = strings[1];
			try {
				Log.v("INSERT CLIENT","Sending message to replica "+port);
				Socket sock = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
						Integer.parseInt(port));
				PrintWriter writer = new PrintWriter(sock.getOutputStream(), true);
				writer.println(msg);
				writer.flush();
			//	Log.v("INSERT CLIENT", "Message sent");
				//sock.close();
			} catch (UnknownHostException e) {
				Log.e("INSERT CLIENT" , "CLIENT TASK "+msg+" "+ e);
			} catch (IOException e) {
				Log.e("INSERT CLIENT" , "CLIENT TASK "+msg+" "+ e);
			} catch (Exception e) {
				Log.e("INSERT CLIENT" , "CLIENT TASK "+msg+" "+ e);
			}
			return null;
		}
	}


	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {


				ServerSocket serverSocket = sockets[0];
				Scanner in = null;
				PrintWriter writer = null;
				Log.v("SERVER "+Thread.currentThread().getName(),"Server started..Port:"+MYPORT+"##");
				String cmd = "";
				while (true) {
					try {
					Socket sock = null;
					sock = serverSocket.accept();
					in = new Scanner(sock.getInputStream());
					writer = new PrintWriter(sock.getOutputStream(), true);
					cmd = in.nextLine();
					Log.v("SERVER "+Thread.currentThread().getName(),"Server Received "+cmd);
					if(cmd.equals("Insert"))
					{
						String key1 = in.nextLine().trim();
						String value1 = in.nextLine().trim();
						Log.v("SERVER "+Thread.currentThread().getName() +MYPORT,"INSERT INTO REPLICA "+key1+" "+value1);
						insertToFile(key1, value1);
					}
					else if(cmd.equals("Insert Delegated"))
					{
						String key1 = in.nextLine().trim();
						String value1 = in.nextLine().trim();
						Log.v("SERVER"+Thread.currentThread().getName() +MYPORT,"Delegated insert reached "+key1+" \n "+value1);
						ContentValues keyValue = new ContentValues();
						keyValue.put("key",key1);
						keyValue.put("value",value1);
						SimpleDynamoActivity.mContentResolver.insert(getUri(),keyValue);

						writer = new PrintWriter(sock.getOutputStream(), true); // dummy values to detect socket failure
						writer.println("message has been received");
						writer.flush();
						Log.v("SERVER","DELEGATION SUCCESS. Dummy value sent");

					} else if(cmd.contains("QUERY_ALL")) {
						Properties prop = new Properties();
						InputStream is = null;
						/*File path = SimpleDynamoActivity.context.getFilesDir();
						File file = new File(path, "data.properties");*/
						File file = new File(path,"data.properties");
						// String[] fileList = getContext().fileList();



						try
						{
							synchronized (this) {
								if (!file.exists())
									file.createNewFile();
								is = new FileInputStream(file);
								prop.load(is);
								StringBuilder sb = new StringBuilder();
								sb.append("Dummy value\n");
								for (Object k : prop.keySet()) {
									String key1 = (String) k;
									String val1 = prop.getProperty(key1);
									sb.append(key1 + "\n").append(val1 + "\n");
								}
								writer = new PrintWriter(sock.getOutputStream(), true);
								writer.println(sb.toString());
								writer.flush();
							}



						} catch (IOException ee) {
							ee.printStackTrace();
						}
					}
					else if(cmd.equals("Query *")) {
						Cursor resultCursor = SimpleDynamoActivity.mContentResolver.query(getUri(), null,
								"@", null, null);
						StringBuilder msg = new StringBuilder();
						if (resultCursor == null || resultCursor.getCount() < 1)  // intentionally sent
						{
							msg.append("empty");
						} else {
							writer = new PrintWriter(sock.getOutputStream(), true);
							resultCursor.moveToFirst();
							while (resultCursor.isAfterLast() == false) {
								String key1 = resultCursor.getString(resultCursor.getColumnIndex("key"));
								String value1 = resultCursor.getString(resultCursor.getColumnIndex("value"));
								msg.append(key1 + "\n" + value1 + "\n");
								resultCursor.moveToNext();
							}

						}
						Log.v("SERVER "+Thread.currentThread().getName(), "message " + msg.toString());
						writer.println(msg.toString());
						writer.flush();
					}else if(cmd.equals("TEST"))
					{
						writer = new PrintWriter(sock.getOutputStream(), true);
						writer.println("This node has not failed");
						writer.flush();
					}
					else if(cmd.equals("Query Delegated"))
					{
						String key = in.nextLine();
						Log.v("SERVER "+Thread.currentThread().getName(),"Delegated query received "+key);
						String value = getValueFromFile(key);
						writer = new PrintWriter(sock.getOutputStream(), true);
						writer.println(value);
						writer.flush();
					}
					else if(cmd.equals("DELETE"))
					{
						/*File path = SimpleDynamoActivity.context.getFilesDir();  // check
						File file = new File(path,"data.properties");*/
						File file = new File(path,"data.properties");
						if(!file.exists())
							try {
								file.createNewFile();
							} catch (IOException e) {
								e.printStackTrace();
							}

						Properties prop = new Properties();
						InputStream is = null;
						String key1 = in.nextLine();

						try {
							synchronized (this) {
								is = new FileInputStream(file);
								prop.load(is);

								prop.remove(key1);
								OutputStream out = new FileOutputStream(file);
								prop.store(out, null);
							}
						}catch(Exception e)
						{
							Log.e("SERVER", "DELETE FAILED for "+key1);
						}

					}
					else if(cmd.equals("DELETE DELEGATE"))
					{
						String key1 = in.nextLine();
						Log.v("SERVER","Delete key "+key1+" from avd "+MYPORT);
						SimpleDynamoActivity.mContentResolver.delete(getUri(),key1,null);
					}
					writer.close();
					in.close();
				}//main try
				catch(IOException e)
				{
					Log.e("SERVER "+Thread.currentThread().getName(), "IOException " + cmd+" "+e);
				}catch(Exception e){
					Log.e("SERVER "+Thread.currentThread().getName(), "Exception "+cmd+" "+e);
				}

			} //while ends
		}
	}

	private class OnCreateTask extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(String... strings) {
			String msg = strings[0];
			keyOrder = new HashSet<String>();
			Log.v("ONCREATE","CALLED");
			HashAvd prev1 = getPrev(curr);
			HashAvd prev2 = getPrev(prev1);
			HashAvd nexxt = getNext(curr);
			HashMap<HashAvd, Boolean> tempMap = new HashMap<HashAvd, Boolean>();
			tempMap.put(prev1, true);
			tempMap.put(prev2 ,true);
			tempMap.put(nexxt, true);

			for(HashAvd avd: tempMap.keySet())
			{
				try {
					Socket sock = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), avd.portNumber);
					PrintWriter writer = new PrintWriter(sock.getOutputStream());
					writer.println("TEST");
					writer.flush();
					Scanner in = new Scanner(System.in);
					String line = in.nextLine();
					Log.v("ONCREATE",line);
				} catch (Exception e) {
					tempMap.put(avd,false);
					Log.v("ONCREATE", "New Failed node detected "+avd.portNumber);
					break;
				}
			}

			HashSet<HashAvd> tempSet = new HashSet<HashAvd>();
			if(tempMap.get(prev2) == false) {
				tempSet.add(prev1);
				tempSet.add(nexxt);
			}
			else if(tempMap.get(prev1) == false){
				tempSet.add(prev2);
				tempSet.add(nexxt);
			}
			else if(tempMap.get(nexxt) == false)
			{
				tempSet.add(prev1);
				tempSet.add(prev2);
				tempSet.add(getNext(nexxt));
			}
			else
			{
				tempSet.add(prev1);
				tempSet.add(prev2);
				tempSet.add(nexxt);
			}

			for(HashAvd avd: tempSet)
			{
				Log.v("ONCREATE","hitting avd "+avd.portNumber);
				try
				{

					Socket sock = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), avd.portNumber);
					PrintWriter writer = new PrintWriter(sock.getOutputStream(), true);
					writer.println(msg);
					writer.flush();
					Scanner in = new Scanner(sock.getInputStream());
					String line = in.nextLine();
					Log.v("ONCREATE", line);
					while(in.hasNext())
					{
						String key = in.nextLine().trim();
						String val = in.nextLine();

						if(keyOrder.contains(key))
							continue;
						val = val.replace("\n","").trim();
						Log.v("ONCREATE", "Value "+val);
						Log.v("ONCREATE","Key "+key);

						int port = Integer.parseInt(val.split(" ")[1]);
    					Log.v("ONCREATE","PORT "+port);

						if(tempMap.get(prev2) == false)
						{
							if(avd.portNumber == prev1.portNumber && (port == prev1.portNumber || port == prev2.portNumber)){
								insertToFile(key, val);
								Log.v("ONCREATE", "key "+key+" val "+val);
							}
							else if(port == curr.portNumber){
								insertToFile(key, val);
								Log.v("ONCREATE", "key "+key+" val "+val);
							}
						}

						else if(tempMap.get(prev1) == false)
						{
							if(avd.portNumber == prev2.portNumber && (port == prev2.portNumber)){
								insertToFile(key, val);
								Log.v("ONCREATE", "key "+key+" val "+val);
							}
							else if(port == curr.portNumber || port == prev1.portNumber){
								insertToFile(key, val);
								Log.v("ONCREATE", "key "+key+" val "+val);
							}
						}

						else
						{
							if( (avd.portNumber == getNext(nexxt).portNumber || avd.portNumber == nexxt.portNumber) && port == curr.portNumber){
								insertToFile(key, val);
								Log.v("ONCREATE", "key "+key+" val "+val);
							}
							else if(port == avd.portNumber && (avd.portNumber != nexxt.portNumber && avd.portNumber != getNext(nexxt).portNumber)){
								insertToFile(key, val);
								Log.v("ONCREATE", "key "+key+" val "+val);
							}
						}
					}
				}
				catch(Exception e)
				{
					Log.e("ONCREATE","Might not be important "+avd.portNumber+ " "+e);
					e.printStackTrace();
				}
			}
			return null;
		}
	}
}
