package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Vector;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {

	/**
	 * Allocate a new process.
	 */
	public UserProcess() {

		pID = processCounter++;
		runningProcessCounter++;
		
		// stdin and stdout;
		openFile[0] = UserKernel.console.openForReading();
		openFile[1] = UserKernel.console.openForWriting();
		
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

//		UThread temp = new UThread(this);
//		temp.setName(name).fork();
//		this.thread = temp;
		
		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	file	the name of the file containing the executable.
	 * @param	argc	the number of arguments to pass to the executable.
	 * @param	argv	the arguments to pass to the executable.
	 * @return	-1 on error, pID otherwise.
	 */

	private int handleExec(int address, int argc, int argvAddr) {
		
		String file = readVirtualMemoryString(address, 256);
		
		if (argc < 0 || argvAddr < 0 || argvAddr > numPages * pageSize || file == null) {
			return -1;
		}
		
		String[] args = new String[argc];
		
		for (int i = 0; i < argc; i++) {
			// read data into virtual memory;
			byte[] virAddr = new byte[4];
			
			if (readVirtualMemory(argvAddr + i * 4, virAddr) > 0) {
				args[i] = readVirtualMemoryString(Lib.bytesToInt(virAddr, 0), 256);
			}
			
			// child processes and add to new processes;
			UserProcess process = UserProcess.newUserProcess();
			
			// and execute;
			if (!process.execute(file, args)) {
				// fails to open the file;
				return -1;
			}
			
			process.parent = this;
			
			// add childrenProcess to list;
			children.add(process);
			
			return process.pID;
			
		}
		
		return 0;
		
	}
	
	private int handleExit(int status) {
		coff.close();
		
		// close all the open process;
		for (int i = 0; i < 16; i++) {
			if (openFile[i] != null) {
				openFile[i].close();
				openFile[i] = null; // kind of like handleClose();
			}
		}
		
		this.status = status;
		exitStat = true;	// exit normally;
		
		// if there's parent proces, enter from parent's side and awake;
		if (parent != null) {
			joinLock.acquire();
			joinCond.wake();
			joinLock.release();
			
			parent.children.remove(this);
			
		}
		
		// now free memory;
		unloadSections();
		
		// and mark process as done;
		KThread.finish();


		// if last process, close;
		if (runningProcessCounter == 1) {
			Machine.halt();
		}
		
		processCounter--;
		
		return 0;
		
	}
	
	private int handleJoin(int pID, int statAddr) {
		UserProcess process = null;
		
		// make sure join's process is child;
		for (int i = 0; i < children.size(); i++) {
			if (pID == children.get(i).pID) {
				// if it is this process then return;
				process = children.get(i);
				break;
			}
		}
		
		if (process == null || process.thread == null) {
			return -1;
		}
		
		// now acquire lock and sleep;
		process.joinLock.acquire();
		process.joinCond.sleep();
		process.joinLock.release();
		
		byte[] childStats = new byte[4];
		
		// take child process out of its status;
		childStats = Lib.bytesFromInt(process.status);
		
		int numByte = writeVirtualMemory(statAddr, childStats);
		
		// if the exit status is normal and numberBytes also normal;
		if (process.exitStat && numByte == 4) {
			return 1;
		}
		
		return 0;

	}
	

	public UThread getThread() {
		return thread;
	}

	/**
	 * Terminate the current process immediately. Any open file descriptors 
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process
	 * 
	 * Status is returned to the parent process as this process's exit status and
	 * can be collected using the join syscall. A process exiting normally should
	 * (but is not required to) set status to 0.
	 * 
	 * exit() never returns.
	 */


	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 */
	public String 
		MemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}
	
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}


	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @param	offset	the first byte to write in the array.
	 * @param	length	the number of bytes to transfer from virtual memory to
	 *			the array.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
		
		byte[] memory = Machine.processor().getMemory();

		// calculate the length;
		if (length > (pageSize * numPages - vaddr)) {
			length = pageSize * numPages - vaddr;
		}
		
		// if does not fit, then cut it off;
		if (data.length - offset < length) {
			length = data.length - offset;
		}
		
		int transByte = 0;
		
		do {
			// calculate page number;
			int pageNumber = Processor.pageFromAddress(vaddr + transByte);
			
			// take in considerate when there is error;
			if (pageNumber < 0 || pageNumber >= pageTable.length) {
				return 0;
			}
			
			// calculate offset;
			int pageOffset = Processor.offsetFromAddress(vaddr + transByte);
			
			// calculate the remaining; 
			int remaining = pageSize - pageOffset;
			
			// calculate the amount for the next trans;
			int amount = Math.min(remaining, length - transByte);
			
			// calculate physical address;
			int physAddr = pageTable[pageNumber].ppn * pageSize + pageOffset;

			// now arraycopy to the physical address;
			System.arraycopy(memory, physAddr, data, offset + transByte, amount);
			
			transByte = transByte + amount;
			// transByte += amount; ?
			
		} while (transByte < length);
		
		return transByte;
	
	}


	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @param	offset	the first byte to transfer from the array.
	 * @param	length	the number of bytes to transfer from the array to
	 *			virtual memory.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		
		// similar to readVirtualMemory;
		
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		

		// calculate the length;
		if (length > (pageSize * numPages - vaddr)) {
			length = pageSize * numPages - vaddr;
		}
		
		// if does not fit, then cut it off;
		if (data.length - offset < length) {
			length = data.length - offset;
		}
		
		int transByte = 0;
		
		do {
			// calculate page number;
			int pageNumber = Processor.pageFromAddress(vaddr + transByte);
			
			// take in considerate when there is error;
			if (pageNumber < 0 || pageNumber >= pageTable.length) {
				return 0;
			}
			
			// calculate offset;
			int pageOffset = Processor.offsetFromAddress(vaddr + transByte);
			
			// calculate the remaining; 
			int remaining = pageSize - pageOffset;
			
			// calculate the amount for the next trans;
			int amount = Math.min(remaining, length - transByte);
			
			// calculate physical address;
			int physAddr = pageTable[pageNumber].ppn * pageSize + pageOffset;

			// now arraycopy to the physical address;
			System.arraycopy(data, offset + transByte, memory, physAddr, amount);
			
			transByte = transByte + amount;
			// transByte += amount; ?
			
		} while (transByte < length);
		
		return transByte;
	
	}
		
	public boolean manageParent(int parID, boolean add) {
		if(add) {
			if(this.parentID != -1) {
				return false;
			}
			else {
				this.parentID = parID;
				return true;
			}
		}
		if(this.parentID == -1) {
			return false;
		}
		this.parentID = -1; 
		return true;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
					argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 *
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		
		UserKernel.memoryLock.acquire();

		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			
			UserKernel.memoryLock.release();
			
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		
		for (int i = 0; i < numPages; i++) {
			int pageNext = UserKernel.memoryList.remove();
			pageTable[i] = new TranslationEntry(i, pageNext, true, false, false, false);
		}
		
		UserKernel.memoryLock.release();
		
		
		// load sections
		// modified; 
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				pageTable[vpn].readOnly = section.isReadOnly();

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		
		UserKernel.memoryLock.acquire();
		
		for (int i = 0; i < numPages; i++) {
			UserKernel.memoryList.add(pageTable[i].ppn);
			pageTable[i] = null;
		}
	
		UserKernel.memoryLock.release();
		
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call. 
	 */
	private int handleHalt() {
		if (pID == 0) {
			Machine.halt();
		}
		
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}


private int searchSpace() {
	// should find the suitable space and return available space;
	for (int i = 0; i < 16; i++) {
		if (openFile[i] == null) {
			return i;
		}
	}
	
	return -1;
	
}
	
	private int handleCreat(int address) {

		// read file name;
		String file = readVirtualMemoryString(address, 256);

		// if file does not exist, create failed;
		if (file == null) {
			return -1;
		}

		// Search for empty space; 
		int fileDescriptor = searchSpace();
		
		/* if searchSpace returns -1, meaning it reached
		   16 max opening file. */
		if (fileDescriptor == -1) {
			return -1;
		}

		// create;
		else {	
			// create: if file does not exist;
			openFile[fileDescriptor] = ThreadedKernel.fileSystem.open(file, true);
			}
		
		return fileDescriptor;
		
	}


	private int handleOpen(int address) {

		// handles open() by opening a file;
		
		String file = readVirtualMemoryString(address, 256);

		// cannot open file does not exist. 
		if (file == null) {
			return -1;
		}

		// search for empty space;
		int fileDescriptor = searchSpace();

		/* if searchSpace returns -1, meaning it reached
		   16 max opening file. */
		if (fileDescriptor == -1) {
			return -1; 
		}
		
		else {
			
			openFile[fileDescriptor] = ThreadedKernel.fileSystem.open(file, false);
			return fileDescriptor;	// open successfully and return the fileDescriptor;
		}
	
	}

	private int handleRead(int fileDescriptor, int bufferAddr, int length) {
		// handle read();
		
		if (openFile[fileDescriptor] == null || fileDescriptor > 15 || fileDescriptor < 0) {
			// return error;
			return -1;
		}

		byte temp[] = new byte[length];

		// for reading file;
		int read = openFile[fileDescriptor].read(temp, 0, length);
		
		// couldn't read data;
		if(read <= 0) {
			return 0;
		}

		int write = writeVirtualMemory(bufferAddr, temp);
		return write;
	}	

	private int handleWrite(int fileDescriptor, int bufferAddr, int length) {
		// write data from virtual memory address into the file;

		// should not be greater than 15 or less than 0;
		if (openFile[fileDescriptor] == null || fileDescriptor > 15 || fileDescriptor < 0) {
			return -1;
		}

		byte temp[] = new byte[length];

		// store data into the temp buffer table;
		int read = readVirtualMemory(bufferAddr, temp);

		if (read <= 0) {
			// no data read;
			return 0;
		}

		// now write the data in;
		int write = openFile[fileDescriptor].write(temp, 0, length);

		if (write < length) {
			// error occurred when writing, return error;
			return -1;
		}

		// return written; 
		return write;
	}

	private int handleClose(int fileDescriptor) {

		// should not be greater than 15 or less than 0;
		if (openFile[fileDescriptor] == null || fileDescriptor > 15 || fileDescriptor < 0) {
			return -1;
		}

		openFile[fileDescriptor].close();
		openFile[fileDescriptor] = null;

		return 0;
	}


	private int handleUnlink(int address) {
		// use for removing file;

		// first get the name of the file;
		String file = readVirtualMemoryString(address, 256);

		// if the file does not exist, no need to delete;
		if (file == null) {
			return 0;
		}

		if (ThreadedKernel.fileSystem.remove(file)) {
			return 0;	// deleted successfully;
		}

		else {
			return -1;
		}

	}


	private static final int
	syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 * 
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		case syscallCreate:
			return handleCreat(a0);

		case syscallOpen:
			return handleOpen(a0);

		case syscallRead:
			return handleRead(a0, a1, a2);

		case syscallWrite:
			return handleWrite(a0, a1, a2);

		case syscallClose:
			return handleClose(a0);

		case syscallUnlink:
			return handleUnlink(a0);

		case syscallExec:
			return handleExec(a0, a1, a2);

		case syscallExit:
			return handleExit(a0);
			
		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3)
					);
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;				       

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	//list of threads associated with this process
	private UThread thread = null;
	//private LinkedList<UThread> threads;

	
	
	protected OpenFile[] openFile = new OpenFile[16]; 
	public LinkedList <UserProcess> children = new LinkedList();
	protected UserProcess parent = null;
	
	private static int processCounter = 0; // counts the process;
	private static int runningProcessCounter = 0; // counts running process;

	// assuming lock is needed;
	protected Lock joinLock = new Lock();
	protected Condition joinCond = new Condition(joinLock);
	
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	//list of children
	// protected LinkedList <UserProcess> children;
	//	protected Hashtable <Integer, UserProcess> children;
	
	private int initialPC, initialSP;
	private int argc, argv;
	
	/* ID of this process and status of process */
	private int pID = 0;
	public int status = 0;
	public boolean exitStat = false; // if the process is exit normally;

	//ID of the parent of this process, -1 means no parent
	private int parentID = -1;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
}
