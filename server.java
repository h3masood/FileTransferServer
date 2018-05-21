import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.io.*;

class server {

	private boolean shutdown;
	private ArrayList<UDPair> peers;

	/*
	 * TerminationCommandReceivedException
	 * This exception is thrown when a client issues the termination command
	 */
	class TerminationCommandReceivedException extends Exception {

		String msg;

		public TerminationCommandReceivedException(String msg)
		{
			this.msg = msg;
		}
	}

	public static void main(String args[])
	{
		try {
			server server = new server();
			server.run();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	} //main


	public server()
	{
		shutdown = false;
		peers = new ArrayList();
	} //Server

	public void run() throws IOException
	{
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		Selector networkChannels;
		int port = 1024;
		while (true) {
			try {
				serverChannel.bind(new InetSocketAddress(port));
				// configure channel to run in non-blocking mode
				serverChannel.configureBlocking(false);
				networkChannels = Selector.open();
				serverChannel.register(networkChannels, SelectionKey.OP_ACCEPT);
				// write port number to file 'port'
				Writer writer = new FileWriter(new File("port"));
				String content = Integer.toString(port);
				writer.write(content, 0, content.length());
				writer.flush();
				break;
			}
			catch (IOException e) {
				port++;
			}
		} //while

		// listen for and accept connections
		// process client requesting as well as match uploaders with corresponding downloaders
		ByteBuffer byteBuffer = ByteBuffer.allocate(9);
		String channelType;
		while (true) {
			if (shutdown && peers.isEmpty()) {
				// shutdown the server
				networkChannels.close();
				break;
			}

			try {
				networkChannels.select();
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			Set<SelectionKey> readyKeys = networkChannels.selectedKeys();
			Iterator<SelectionKey> iterator = readyKeys.iterator();
			SelectionKey key;
			while (iterator.hasNext()) {
				key = iterator.next();
				iterator.remove();
				try {
					if (!key.isValid()) {
						// ASSUMPTION: an uploader closed the channel
						// Therefore, close the corresponding downloader channel
						UDPair pair = this.findInPeers(key);
						if (pair == null) continue;
						pair.destroy();
						peers.remove(pair);
					}
					else if (key.isAcceptable() && !shutdown) {
						// accept a new client channel only if "F" command has not been given so far
						ServerSocketChannel server = (ServerSocketChannel) key.channel();
						// accept new client connection
						SocketChannel client = server.accept();
						// read the control message
						client.read(byteBuffer);
						// configure client channel to run in non-blocking mode
						client.configureBlocking(false);
					  byteBuffer.flip();
						channelType = new String(byteBuffer.array(), "ASCII");
						client.configureBlocking(false);
						int end = channelType.indexOf("\0");
						if (end == -1) {
							this.acceptClient(channelType.substring(0,1), channelType.substring(1), client, networkChannels);
						}
						else if (end != -1) {
							this.acceptClient(channelType.substring(0,1), channelType.substring(1, end), client, networkChannels);
						}
					}
					else if (key.isWritable()) {
						UDPair pair = this.findInPeers(key); // find UDPair to which this key belongs
						if (pair != null && pair.isReadyForReading(iterator)) pair.transferData();
						SocketChannel channel = (SocketChannel) key.channel();
						if (!channel.isOpen()) {
							pair.destroy();
							peers.remove(pair);
						}
					}
					else if (key.isReadable()) {
						UDPair pair = this.findInPeers(key);
						if (pair != null && pair.isReadyForWriting(iterator)) pair.transferData();
						SocketChannel channel = (SocketChannel) key.channel();
						if (!channel.isOpen()) {
							pair.destroy();
							peers.remove(pair);
						}
					}
				}
				catch (TerminationCommandReceivedException e) {
					shutdown = true;
					removeAllUnmatched();
					// stop listening for new connections
					serverChannel.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				catch (NullPointerException e) {
					e.printStackTrace();
				}
			} //while
		} //while
	} //run

	/* acceptClient
	 * accepts an incoming client connection
	 */
	public void acceptClient(String cmd, String key, SocketChannel client, Selector selector)
	throws TerminationCommandReceivedException
	{
		try {
			// ASSUMPTION: downloader always starts before its corresponding uploader
			SelectionKey clientKey = null;
			if (cmd.equals("G")) { // downloader
				UDPair pair = new UDPair(key);
				// register as write-only channel
				clientKey = client.register(selector, SelectionKey.OP_WRITE);
				assert(clientKey != null);
				pair.addDownloader(client, clientKey);
				peers.add(pair);
			}
			if (cmd.equals("P")) { //uploader
				// find corresponding downloader
				Iterator<UDPair> iterator = peers.iterator();
				UDPair pair;
				while (iterator.hasNext()) {
					pair = iterator.next();
					if (pair.isSameKey(key)) {
						// register it as a read-only channel
						clientKey = client.register(selector, SelectionKey.OP_READ);
						assert(clientKey != null);
						pair.addUploader(client, clientKey);
						System.out.println("Matched " + cmd + key);
					}
				} //while
			}
			if (cmd.equals("F")) { // termination request issued
				throw new TerminationCommandReceivedException("terminate");
			}
		}
		catch (TerminationCommandReceivedException e) {
			throw e;
		}
		catch (ClosedChannelException e) {
			e.printStackTrace();
		}
	} //acceptClient


	protected UDPair findInPeers(SelectionKey key)
	{
		Iterator<UDPair> iterator = peers.iterator();
		SocketChannel channel = (SocketChannel) key.channel();
		UDPair pair;
		while (iterator.hasNext()) {
			pair = iterator.next();
			if (pair.isHomeToUploader(channel) || pair.isHomeToDownloader(channel)) {
				return pair;
			}
		} //while
		return null;
	} //findInPeers


	/*
	 * removeAllUnmatched()
	 * removes all unmatched clients from peers
	 */
	protected void removeAllUnmatched()
	{
		Iterator<UDPair> iterator = peers.iterator();
		UDPair pair;
		while (iterator.hasNext()) {
			try {
				pair = iterator.next();
				if (pair.isUnmatched()) {
					pair.destroy();
					iterator.remove();
				}
			}
			catch (UnsupportedOperationException e) {
				e.printStackTrace();
			}
			catch (IllegalStateException e) {
				e.printStackTrace();
			}
		} //while
	} //shutdown

} //Server


class UDPair {

	String key; // key used to match uploader with downloader
	SelectionKey uKey; // uploader's selection key
	SelectionKey dKey; // downloader's selection key
	SocketChannel uploader;
	SocketChannel downloader;
	private final int BUFFER_SIZE = 1000;
	private ByteBuffer byteBuffer;

	public UDPair(String key)
	{
		this.key = key;
		byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
	}

	public boolean isSameKey(String key)
	{
		try {
			return this.key.equals(key);
		}
		catch (NullPointerException e) {
			return false;
		}
	}

	public String getKey()
	{
		return new String(key);
	}

	public void addUploader(SocketChannel u, SelectionKey key)
	{
		uKey = key;
		uploader = u;
	} //addUploader

	public void addDownloader(SocketChannel d, SelectionKey key)
	{
		dKey = key;
		downloader = d;
	} //addDownloader


	public boolean isHomeToUploader(SocketChannel channel)
	{
		try {
			return this.uploader == channel;
		}
		catch (NullPointerException e) {
			return false;
		}
	} //isHomeToUploader


	public boolean isHomeToDownloader(SocketChannel channel)
	{
		try {
			return this.downloader == channel;
		}
		catch (NullPointerException e) {
			return false;
		}
	}


	public boolean isReadyForWriting(Iterator<SelectionKey> readyChannels)
	{
		if (dKey == null) return false;
		return dKey.isWritable();
	} //readyForWriting

	public boolean isReadyForReading(Iterator<SelectionKey> readyChannels)
	{
		if (uKey == null) return false;
		return uKey.isReadable();
	} //readyForReading


	/*
	 * transferData()
	 * receives data from uploader and sends it to the downloader
	 * ASSUMPTION: uploader and downloader SocketChannels are in
	 *             in non-blocking mode so a call to this function
	 *             never blocks
	 */
	public void transferData()
	{
		try {
			// ensure byteBuffer is ready for use
			byteBuffer.clear();
			int read;
			read = uploader.read(byteBuffer);
			if (read == -1) {
				this.destroy();
			}
			else if (read > 0) {
				byteBuffer.flip(); // reset position to index 0
				downloader.write(byteBuffer);
				byteBuffer.clear(); // prepare for resuse
			}
		}
		catch (ClosedChannelException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (NotYetConnectedException e) {
			e.printStackTrace();
		}
	} //transferData


	public boolean isUnmatched()
	{
		if (uploader == null || downloader == null) return true;
		return false;
	}


	public void destroy()
	{
		try {
			if (uploader != null) uploader.close();
			if (downloader != null) downloader.close();
			if (uKey != null) uKey.cancel();
			if (dKey != null) dKey.cancel();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	} //destroy
} //UDPair
