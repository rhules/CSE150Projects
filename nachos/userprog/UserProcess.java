package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Vector;
import java.util.Arrays;
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
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		pID = UserKernel.gPID++;
		children = new LinkedList<UserProcess>();
		for (int i=0; i<numPhysPages; i++) 
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);

		// supports up to 16 files;
		openFile = new OpenFile[16];

		openFile[0] = UserKernel.console.openForReading();
		openFile[1] = UserKernel.console.openForWriting();
		UserKernel.processList.add(this);
		this.threads = new LinkedList<UThread>();
	}

	//Allocate a new process with a parent


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

		UThread temp = new UThread(this);
		temp.setName(name).fork();
		this.threads.add(temp);

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

	public int exec(int address) {
		String[] av = new String[argc];
		int off = 0;
		for(String s:av) {
			byte[] temp = s.getBytes();
			readVirtualMemory(argv, temp, off, argc);
			off += s.length()*4;
			s = temp.toString();
			
		}
		//int transferred = readVirtualMemory(argv, av.getBytes(), 0, argc);
		String file = readVirtualMemoryString(address, 256);

		// cannot open file does not exist. 
		if(file == null||!file.endsWith(".coff")||!load(file, av)||argc < 0 || argv > numPages * pageSize) {
			return -1;
		}

		String[] arg = new String[argc];

		// store data into virtual memory;
		for (int i = 0; i < argc; i++) {
			byte[] argAddr = new byte[4];

			// read virtual memory address;
			if (readVirtualMemory(argv + i * 4, argAddr) > 0) {
				// reading byte by byte;
				arg[i] = readVirtualMemoryString(Lib.bytesToInt(argAddr, 0), 256);
			}
		}

		UserProcess temp = UserProcess.newUserProcess();

		// fail opening the file;
		if (!temp.execute(file, arg)) {
			return -1;
		}

		temp.manageParent(this.pID, true);
		this.children.add(temp);

		return temp.pID;
	}

	public boolean isChild(int ID) {
		Iterator<UserProcess> i = children.iterator();
		while(i.hasNext()) {
			if(i.next().pID == ID) {
				return true;
			}
		}
		return false;
	}

	public int join(int processID, int status) {
		//Lib.assertTrue(this != UserKernel.currentProcess());
		if(this == UserKernel.currentProcess()) {
			boolean machineStatus = Machine.interrupt().disable();
			KThread.sleep();
			Machine.interrupt().restore(machineStatus);
		}

		if(status == -1) {
			return 0;
		}
		while(isChild(processID)) {

			boolean machineStatus = Machine.interrupt().disable();
			//return if the given process has ended
			if(status == 0|| status == -1) {
				for(UserProcess i:children) {
					if(i.pID == processID) {
						i.manageParent(this.pID, false);
						children.remove(i);
						if(status == 0) {
							return 1;
						}
						else {
							return 0;
						}
					}
				}
				return -1;
			}
			else {

				//sleep
				UserProcess temp = children.element();
				for(UserProcess i:children) {
					if(i.pID == processID) {
						temp = i;
					}
				}
				for(UThread i:threads) {
					i.join(status, temp);
				}
				Machine.interrupt().restore(machineStatus);
			}
			//return 1;
		}
		return -1;
	}

	public UThread getFirstThread() {
		return threads.element();
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

	public void exit(int status) {
		coff.close();
		//close any open files
		for(int i = 15; i > 1; i--) {
			if(openFile[i] != null) {
				handleSyscall(8, i, 0, 0, 0);
			}
		}
		//disown children
		for(UserProcess i: children) {
			i.manageParent(this.pID, false);
			children.remove(i);
		}
		//remove this process from the process list
		UserKernel.processList.remove(this);
		//If process list is empty, end simulation
		if(UserKernel.processList.isEmpty()) {
			Machine.terminate();
		}
	}

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

		// for now, just assume that virtual addresses equal physical addresses
		//		if (vaddr < 0 || vaddr >= memory.length)
		//			return 0;


		if (length > (pageSize * numPages - vaddr)) {
			length = pageSize * numPages - vaddr;
		}

		// if length is too big, then cut it off;
		if ((data.length - offset) < length) {
			length = data.length - offset;
		}

		// successfully transferred bytes;
		int bytes = 0;

		do {
			// calculate page number;
			int pageNumber = Processor.pageFromAddress(vaddr + bytes);

			// page number should not be grater than the page size and should not be negative;
			if (pageNumber >= pageTable.length || pageNumber < 0) {
				return 0;
			}

			// calculate page offset;
			int calcOffset = Processor.offsetFromAddress(vaddr + bytes);

			// now calculate the remaining amount;
			int bytesRemaining = pageSize - calcOffset;

			// and calculate the next amount: the min of what's remaining;
			int amount = Math.min(bytesRemaining, length - bytes);

			// calculate physical address;
			int phyAddr = (pageTable[pageNumber].ppn * pageSize) + calcOffset;

			// now move from what's stored in the physical address to virtual mem;
			System.arraycopy(memory, phyAddr, data, offset + bytes, amount);

			// fix what's successfully transferred;
			bytes = bytes + amount;

		} while(bytes < length);

		return bytes;
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
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		//if (vaddr < 0 || vaddr >= memory.length)
		//	return 0;

		if (length > (pageSize * numPages - vaddr)) {
			length = (pageSize * numPages) - vaddr;
		}
		
		if ((data.length - offset) < length) {
			length = data.length - offset;
		}
		
		int bytes = 0;

		do {
			// calculate page number;
			int pageNumber = Processor.pageFromAddress(vaddr + bytes);

			/* page number should not be grater than the page size and should
		   not be negative; */
			if (pageNumber >= pageTable.length || pageNumber < 0) {
				return 0;
			}

			// calculate page offset;
			int calcOffset = Processor.offsetFromAddress(vaddr + bytes);

			// now calculate the remaining amount;
			int bytesRemaining = pageSize - calcOffset;

			// and calculate the next amount: the min of what's remaining;
			int amount = Math.min(bytesRemaining, length - bytes);

			// calculate physical address;
			int phyAddr = (pageTable[pageNumber].ppn * pageSize) + calcOffset;

			// now move from what's stored in the physical address to virtual mem;
			System.arraycopy(data, offset + bytes, memory, phyAddr, amount);

			// fix what's successfully transferred;
			bytes = bytes + amount;

		} while(bytes < length);

		return bytes;
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
		// modified
		UserKernel.memoryLock.acquire(); // acquire lock
		// checks		
		
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			
			// release lock
			UserKernel.memoryLock.release();
			
			return false;
		}
		
		// page table;
		pageTable = new TranslationEntry[numPages];
		
		for (int i = 0; i < numPages; i++) {
			// take out from the free list by removing;
			int pageNext = UserKernel.memoryList.remove();
			pageTable[i] = new TranslationEntry(i, pageNext, true, false, false, false);	
		}
		
		// release lock
		UserKernel.memoryLock.release();

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			+ " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;

				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
				/* TranslationEntry entry = pageTable[vpn];
				entry.readOnly = section.isReadOnly();
				int ppn = entry.ppn;

				section.loadPage(i, ppn); */
				
				// label it as read only;
				pageTable[vpn].readOnly = section.isReadOnly();
				// add to physical page table;
				section.loadPage(i, pageTable[vpn].ppn);
				
				
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
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

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}


	private int searchSpace() {
		int fileDescriptor = -1;

		// support 16 files max;
		for (int i = 0; i < 16; i++) 
		{
			if(openFile[i] == null) {
				fileDescriptor = i;
				return fileDescriptor;
			}
		}
		return -1;		
	}

	private int handleCreat(int address) {

		// invalid address;
		if (address < 0) {
			return -1;
		}

		// read file name;
		String file = readVirtualMemoryString(address, 256);

		// if file does not exist, create failed;
		if (file == null) 
		{
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
			OpenFile f =ThreadedKernel.fileSystem.open(file, true);

			if (f == null) {
				return -1;
			}

			else {
				openFile[fileDescriptor] = f;
				return fileDescriptor;
			}

			// openFile [fileDescriptor] = ThreadedKernel.fileSystem.open(file, true);	
		}

		// return fileDescriptor;
		// return fileDescriptor;

	}


	private int handleOpen(int address) {

		// invalid address check;
		if (address < 0) {
			return -1;
		}

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
			// the value of create should be false since we are only handling open right here;

			OpenFile f = ThreadedKernel.fileSystem.open(file, false);

			if(f == null) {
				return -1;
			}

			else {

				//openFile[fileDescriptor] = ThreadedKernel.fileSystem.open(file, false);
				openFile[fileDescriptor] = f;
				return fileDescriptor;
			}
		}		
	}

	private int handleRead(int fileDescriptor, int addr, int l) {
		if (fileDescriptor > 15 || fileDescriptor < 0) {
			return -1;
		}

		else if(openFile[fileDescriptor] == null) {
			return -1;
		}

		byte buffer[] = new byte[l];

		int readNum = openFile[fileDescriptor].read(buffer, 0, l);

		// couldn't read data;
		if(readNum <= 0) {
			return 0;
		}

		int writeNum = writeVirtualMemory(addr, buffer);
		return writeNum;
	}	

	private int handleWrite(int fileDescriptor, int addr, int l) {
		// write data from virtual memory address into the file;

		// should not be greater than 15 or less than 0;
		if (fileDescriptor > 15 || fileDescriptor < 0) {
			return -1;
		}

		else if(openFile[fileDescriptor] == null) {
			return -1;
		}

		byte buffer[] = new byte[l];

		// store data into the temp buffer table;
		int readNum = readVirtualMemory(addr, buffer);

		if (readNum <= 0) {
			// no data read;
			return 0;
		}

		// now write the data in;
		int writeNum = openFile[fileDescriptor].write(buffer, 0, l);

		if (writeNum < l) {
			// error occured when writing, return error;
			return -1;
		}

		// return written; 
		return writeNum;
	}



	private int handleClose(int fileDescriptor) {

		// add comments later;

		// should not be greater than 15 or less than 0;
		if (fileDescriptor > 15 || fileDescriptor < 0) {
			return -1;
		}

		// or if the file does not exist, error;
		else if (openFile[fileDescriptor] == null) {
			return -1;
		}

		else {

			openFile[fileDescriptor].close();
			openFile[fileDescriptor] = null;

		}

		return 0;
	}


	private int handleUnlink(int address) {
		// use for removing file;

		// invalid virtual memory address;
		if (address < 0) {
			return -1;
		}

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

		case syscallExit:
			exit(a0);
			break;
		case syscallExec:
			return exec(a0);
		case syscallJoin:
			return join(a0, a1);

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
	//private UThread thread;
	private LinkedList<UThread> threads;

	protected OpenFile[] openFile; 


	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	//list of children
	protected LinkedList <UserProcess> children;

	private int initialPC, initialSP;
	private int argc, argv;

	//ID of this process
	private int pID;

	//ID of the parent of this process, -1 means no parent
	private int parentID = -1;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
}
