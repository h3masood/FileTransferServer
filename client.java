import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

class client {

	private SocketAddress serverAddr;
	private String message;
	private String filename;
	private int bufferSize;
	private int waitTime; // time in milliseconds

	public static void main(String args[])
	{
		try {
			String host = null, key = null, filename = null;
			int port = 0, waitTime = 0, bufferSize = 0;

			if (args.length != 3 && args.length != 5 && args.length != 6) {
				printErrorMsg();
				return;
			}
			if (args.length == 3 || args.length == 5 || args.length == 6) {
				host = args[0];
				port = Integer.parseInt(args[1]);
				key = args[2];
			}
			if (args.length == 5 || args.length == 6) {
				filename = args[3];
				bufferSize = Integer.parseInt(args[4]);
			}
			if (args.length == 6) {
					waitTime = Integer.parseInt(args[5]);
			}
			if (key != null) key = padKey(key);
			client client = new client(host, port, key, filename, bufferSize, waitTime);
			client.run(); // establish connection and communicate with server
		}
		catch (NumberFormatException e) {
			printErrorMsg();
		}
	} //main


	protected static String padKey(String key)
	{
		int length = key.length();
		if (length == 9) return key;
		StringBuilder newKey = new StringBuilder(key);
		for (int i=length; i < 9; i++) {
			newKey.append("\0");
		}
		return newKey.toString();
	}


	protected static void printErrorMsg()
	{
		String msg = "Client was started incorrectly. The following are the only" +
							   " three correct ways to start the Client program:\n"       +
								 "1. client <host> <port> F\n" +
								 "2. client <host> <port> G<key> <filename> <recv size>\n" +
								 "3. client <host> <port> P<key> <filename> <send size> <wait time>\n";
		System.err.println(msg);
	}

	public client(String host, int port, String type, String filename,
							  int bufSize, int waitTime)
	{
		serverAddr = new InetSocketAddress(host, port);
		message = type;
		if (filename != null) this.filename = filename;
		bufferSize = bufSize;
		this.waitTime = waitTime;
	} //Client

	protected void run()
	{
		String type = Character.toString(message.charAt(0)); // type of client program
		switch (type) {
			case "F":
				terminateServer();
			break;

			case "G":
				download();
			break;

			case "P":
				upload();
			break;
		} //switch
	} //run

	protected void terminateServer()
	{
			try {
				SocketChannel client = SocketChannel.open(serverAddr);
				client.configureBlocking(false);
				ByteBuffer byteBuffer = ByteBuffer.allocate(9);
				System.out.println(message + " sending control packet");
				writeControlMsg(byteBuffer);
				byteBuffer.flip(); // reset positon to index 0
				client.write(byteBuffer);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
 	} //terminateServer

	protected void upload()
	{
		try {
			SocketChannel client = SocketChannel.open(serverAddr);
			client.configureBlocking(false);
			ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
			System.out.println(message + " sending control packet");
			writeControlMsg(byteBuffer);
			byteBuffer.flip(); // reset positon to index 0
			client.write(byteBuffer);
			byteBuffer.clear();
			BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(new File(filename)));
			byte[] bytes = new byte[bufferSize];

			// upload file
			int read; // chars written
			while (true) {
				// CHANGED
				//read = reader.read(cbuf, 0, bufferSize/2);
				byteBuffer.clear();
				read = inStream.read(bytes, 0, bufferSize);
				if (read  == -1) {
					System.out.println(message + " has sent the entire file. End-of-transmission");
					client.close();
					break;
				}
				else if (read > 0) {
					byteBuffer = ByteBuffer.allocate(read);
					byteBuffer.put(bytes, 0, read);
					byteBuffer.flip();
					client.write(byteBuffer);
					// delay subsequent reads
					Thread.sleep(waitTime);
				}
			} //while
		}
		catch (BufferOverflowException e) {
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	} //upload


	protected void download()
	{
		try {
			SocketChannel client = SocketChannel.open(serverAddr);
			client.configureBlocking(false);
			// The following is done to make handling byte to char conversions
			// for the purpose of writing to file easier
			ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
			System.out.println(message + " sending control packet");
			writeControlMsg(byteBuffer);
			byteBuffer.flip(); // reset positon to index 0
			client.write(byteBuffer);
			byteBuffer.clear();
			BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(new File(filename)));
			byte[] bytes = new byte[bufferSize];

			int read; // count of bytes read
			while (true) {
				try {
					byteBuffer.clear();
					read = client.read(byteBuffer);
					if (read == -1) {
						break;
					}
					else if (read > 0) {
						System.out.println("read " + Integer.toString(read) + " bytes");
						byteBuffer.flip();
						byteBuffer.get(bytes, 0, read);
						outStream.write(bytes, 0, read);
					}
				}
				catch (BufferUnderflowException e) {
					System.out.println("BufferUnderflowException");
					continue; // try again
				}
			} //while
			outStream.flush();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void writeControlMsg(ByteBuffer buffer)
	{
		try {
			buffer.put(message.getBytes());
		}
		catch (BufferOverflowException e) {
			e.printStackTrace();
		}
	} //sendControlMsg
} //Client
