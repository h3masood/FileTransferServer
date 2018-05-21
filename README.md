The program was built and tested on the following machines:
	1. My personal macbook
	2. ubuntu1604-008@linux.student.cs.uwaterloo.ca
	3. ubuntu1604-002@linux.student.cs.uwaterloo.ca

Different output messages from clients and their meaning:
 	1. "P/G1 or F sending control packet": this output is from uploader/downloader or terminator right
	    before it sends the control packet.
	2. "P1 has sent the entire file. End-of-transmission": this output is from the uploader after it
	    is done transferring the contents of the file.
	3. "Matched P1": this output is from the server indicating that P1 is matched with G1 by the server
	4. Any other output would be the print of stack trace at the time of an exception

Client Program Design
	1. The design is very simple. Regardless of the type of client, it establishes connection with the
		 server using the given hostname and port via a SocketChannel object
  2. It then sends the control packet followed by any other contents that need to be send and then
	   terminates, closing the SocketChannel object in the process

Server Program Design
	1. There program consists of two classes: Server and UDPair, the latter is used to establish
	   the notion of an uploader/downloader pair.
	2. The Server class uses I/O multiplexing and Java non-blocking I/O library to support concurrent
	   file transfers. All the existing channels whether it be the ServerSocketChannel (used for listening
		 for new connections) or the SocketChannel (used to establish a dedicated connection an uploader/
		 downloader) operate in non-blocking mode. This use of Selector + non-blocking I/O was the
		 preferred choice over multithreading because the I considered the design to be much simpler,
		 and because I wanted to avoid the potential overhead of context switching thus, allowing for
		 a more efficient server design (this is not entirely true since some degree of multithreading
		 would have been better to exploit the present of multiple CPUs in today's hardware).
	3. File transfer only happens after a downloader has been matched to its uploader and no file is
	   temporarily stored on the server.

On Errors/Unfinished Functionality
	I have tested the programs in various different contexts such as when one uploader/downloader pair
	exists or when multiple pairs exist and the programs work fine. I have also tested for other
	functionality such as writing to or reading from file, terminating the server using a client program
	and everything works fine. No errors observed and no functionality has been left unimplemented.
