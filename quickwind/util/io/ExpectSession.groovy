package quickwind.util.io

import java.util.regex.Matcher
import java.util.regex.Pattern

class Logger {
    static void log(msg) {
        println "====== Debug =======[${Thread.currentThread()}]: ${msg}"
    }
}

class OutputSession implements java.io.Closeable {
	OutputStream outputStream
	Thread thread
	Long slow
	final Object sendLock = new Object()
	boolean aborted
	boolean closed = false

	OutputSession(OutputStream outputStream) {
		setOutputStream outputStream
	}

	void send(String outStr) throws Exception {
		send outStr, 0
	}

	void send(String outStr, long timeout) throws Exception {
		send outStr.getBytes(), timeout		
	}

	void send(byte[] outBytes, long timeout) {
		send outBytes, 0, outBytes.length, timeout
	}

	void send(byte[] outBytes, final int outPtr, int outLen, final long timeout) {
		synchronized (sendLock) {
			aborted = false
			if (closed) {
				throw new Exception("OutputSession is closed, cannot send out string: 'new String(outBytes,outPtr,outLen)'")
			}
			if (outputStream == null) {
				throw new Exception("OutputStream is null, cannot send out string: 'new String(outBytes,outPtr,outLen)'")
			}
			if (outLen > 0) {
				thread = Thread.startDaemon(
						{
							if (slow == null) {
								outputStream.write(outBytes, outPtr, outLen)
								outputStream.flush()
								//print new String(outBytes, outPtr, outLen)
                                Logger.log "Sending finished..."
								outBytes = null
								outLen = 0
							} else {
								int ptr = outPtr
								int len = outLen
								while (len > 0) {
									outputStream.write(outBytes, ptr, 1)
									outputStream.flush()
									//print new String(outBytes, ptr, 1)
                                    Logger.log "Sending finished..."
									ptr++
									len--
									if (len != 0 && slow != null) {
										sleep slow
									}
								}
							}
						}
				)
			}
			try {
				if (timeout == 0) {
					thread.join()
				} else {
					thread.join(timeout)
				}
			} catch (InterruptedException ex) {
			}
			if (closed) {
				throw new IllegalStateException("OutputSession was closed while sending string: 'new String(outBytes,outPtr,outLen)'")
			}
			if (aborted) {
				return;
			}
			if (outBytes != null) {
				throw new Exception("Timeout while sending string: 'new String(outBytes,outPtr,outLen)'")
			}
		}
		 
	}

	void abort() {
		aborted = true
		thread?.interrupt()
	}

	void close() {
		closed = true
		abort()
	}
}

class InputSession implements java.io.Closeable {
	static final int buffersize = 20480
	byte[] inputData = new byte[buffersize]
	Thread thread
	StringBuffer stringBuffer = new StringBuffer()
	int caret = 0
	InputStream inputStream
	Set<OutputSession> pipes = new HashSet<OutputSession>()
	boolean aborted
	boolean closed = false
	boolean timedOut
	long expirationTime
	String group

	String receivedUntil
	Object receivedObject
	Object expectations
	
	final Object lock = new Object()

	InputSession(InputStream inputStream) {
		setInputStream inputStream
	}

	void setExpectations(Object expectations) {
		if (expectations instanceof Collection || expectations instanceof Map) {
			this.expectations = expectations
		} else {
			this.expectations = new ArrayList()
			((ArrayList) this.expectations).add(expectations)
		}
	}

	String expect(Object expectations, long timeout) {
		expirationTime = System.currentTimeMillis() + timeout
		synchronized (lock) {
			if (closed) {
				throw new IllegalStateException("Cannot expect on a closed InputSession")
			}
			setExpectations(expectations)
			timedOut = false
			aborted = false
			receivedObject = null
			receivedUntil = null
			Logger.log "Start expection of ${expectations}..."
			if (stringBuffer.length() > caret) {
				if (check(null)) {
					return group
				}
			}
			while (this.expectations != null) {
				if(isClosed()){
					throw new IllegalStateException("InputSession was closed while expecting: $expectations")
				}
				if (isAborted()) {
					return null
				}
				long timeToWait = expirationTime - System.currentTimeMillis()
				if (timeToWait > 0L) {
					//println "waiting $timeToWait ms"
					lock.wait(timeToWait)
				} else {
					//println "timed out"
					this.expectations=null
					timedOut = true
					return null
				}
			}
			return group
		}
	}

	void setInputStream(final InputStream inputStream) {
		if (inputStream != this.inputStream) {
			if (inputStream == null) {
				throw new IllegalArgumentException("Cannot set InputStream to null")
			}
			thread?.interrupt()
			this.inputStream = inputStream
			thread = Thread.startDaemon(
					{
						int total = 0
						while (total >= 0) {
							total = inputStream.read(inputData, 0, buffersize)
							if (total > 0) {
								add(inputData, 0, total)
							}
						}
					}
			)
		}
	}

	void add(byte[] data, int start, int total) {
		if (total > 0) {
			add(new String(data, start, total))
		}
	}

	void add(String string) {
		if (string != null && string.length() > 0) {			
			for (OutputSession pipe: pipes) {
				pipe.send string
			}
			check string
			//Thread.startDaemon {
				print string
                System.out.flush()
			//}
		}
	}

	protected boolean check(String string) {
		synchronized (lock) {
			if (string != null) {
				stringBuffer.append(string)
			}
			if (expectations != null) {
				Integer pos = null
				Object received = null
				expectations.each {Object expectation ->
					if (pos != 0) {
						def o = expectation instanceof Map.Entry ? ((Map.Entry) expectation).key : expectation
						int p = -1
						String g = null
						if (o instanceof Pattern) {
							Matcher m = ((Pattern) o).matcher(stringBuffer)
							if (m.find(caret)) {
								p = m.start()
								g = m.group()
							}
						} else {
							g = o.toString()
							p = stringBuffer.indexOf(g, caret)							
						}
						if (p >= 0 && (pos == null || p < pos)) {
							received = expectation
							group = g
							pos = p
						}
					}
				}
				if (pos != null) {                    
					receivedUntil = stringBuffer.substring(caret, pos)
					caret = pos + group.length()
					receivedObject = received
					expectations = null
                    Logger.log "Found!!! caret: ${caret}, pos: ${pos}, stringBuffer length: ${ stringBuffer.length()}"
					lock.notify()
					return true
				}
			}
		}
		return false
	}

	void abort() {
		aborted = true
		thread?.interrupt()
	}

	void close() {
		synchronized (lock) {
			closed = true
			abort();
		}
	}
}
  
class ExpectSession implements java.io.Closeable {
	static final int buffersize = 2048
	Map<Object, InputSession> inputSessions = new HashMap<Object, InputSession>()
	Map<Object, OutputSession> outputSessions = new HashMap<Object, OutputSession>()

	Map pipes = new HashMap()
	InputSession inputSession
	OutputSession outputSession
	Thread outputThread
	Thread inputThread
	long timeout = 10000

	boolean closed = false

	final sendLock = new Object()

	ExpectSession(Object object) {
		add object
	}

	void setSlow(Long slow) {
		if (outputSession == null) {
			throw new IllegalStateException("Cannot set slow")
		}
		outputSession.setSlow slow
	}

	void add(Object object) {
		if (object == null) {
			throw new IllegalArgumentException("Cannot add null to Session")
		}
		if (!addProcess(object)) {
			throw new IllegalArgumentException("Cannot add '$object' to Session")
		}
	}

	boolean addOutputStream(OutputStream object) {
		if (object == null) {
			return false
		}
		return addOutputStreamEntry(new MapEntry(object, (OutputStream) object))		
	}

	boolean addOutputStreamEntry(Map.Entry entry) {
		if (entry.value instanceof OutputStream) {
			OutputSession old = outputSessions.get(entry.key)
			if (old != null) {
				old.setOutputStream((OutputStream) entry.value)
			} else {
				old = new OutputSession((OutputStream) entry.value)
				outputSessions.put entry.key, old
			}
			if (outputSession == null) {
				outputSession = old
			}
			return true
		}
		return false
	}

	boolean addProcess(Object object) {
		boolean added = false
		if (object.metaClass.respondsTo(object, "getOutputStream")) {
			added |= addOutputStream(object.getOutputStream())
		}
		if (object.metaClass.respondsTo(object, "getInputStream")) {
			added |= addInputStream(object.getInputStream())
		}
		if (object.metaClass.respondsTo(object, "getErrorStream")) {
			added |= addInputStream(object.getErrorStream())
		}
		return added
	}

	boolean addInputStream(InputStream object) {
		if (object == null) {
			return false
		}
		return addInputStreamEntry(new MapEntry(object, (InputStream) object))		
	}

	boolean addInputStreamEntry(Map.Entry entry) {
		if (entry.value instanceof InputStream) {
			InputSession old = inputSessions.get(entry.key)
			if (old != null) {
				old.setInputStream((InputStream) entry.value)
			} else {
				old = new InputSession((InputStream) entry.value)
				inputSessions.put(entry.key, old)
			}
			if (inputSession == null) {
				inputSession = old
			}
			return true

		}
		return false
	}

	void send(Closure outStr) throws Exception {
		send(outStr.call(this).toString())
	}

	void send(String str) throws Exception {
		if (outputSession == null) {
			throw new IllegalStateException("Cannot send, because outputSession is not set")
		} else {
			outputSession.send(str, timeout)
		}
	}

	void send(byte[] outBytes) {
		if (outputSession == null) {
			throw new IllegalStateException("Cannot send, because outputSession is not set")
		} else {
			outputSession.send(outBytes, timeout)
		}
	}

	void send(byte[] outBytes, int outPtr, int outLen) {
		if (outputSession == null) {
			p
			throw new IllegalStateException("Cannot send, because outputSession is not set")
		} else {
			outputSession.send(outBytes, outPtr, outLen, timeout)
		}
	}

	void fail(String failureMessage) {
		throw new IllegalStateException(failureMessage)
	}

	void fail(Closure failureMessage) {
		fail(failureMessage.call(this).toString())
	}

	String expect(Object expectations, long timeout, Closure onTimeout) {
		if (inputSession == null) {
			throw new IllegalStateException("Cannot expect, because inputSession is not set")
		}
		inputSession.expect(expectations, timeout)
		if (inputSession.isClosed()) {
			throw new IllegalStateException("InputSession was closed while expecting: $expectations")
		}
		if (inputSession.isAborted()) {
			return null
		}
		if (inputSession.isTimedOut()) {
			return onTimeout?.call(this)
		}
		Object o = inputSession.getReceivedObject()
		if (o == null) {
			throw new IllegalStateException("InputSession ended without receiving: $expectations")
		}
		if (o instanceof Map.Entry && ((Map.Entry) o).value instanceof Closure) {
			return ((Closure) ((Map.Entry) o).value).call(this)
		}
		return inputSession.getGroup()
	}


	String expect(Object expectations, Closure onTimeout) {
		return expect(expectations, timeout, onTimeout)
	}

	String expect(Object expectations, long timeout) {
		return expect(expectations, timeout, {ExpectSession it -> it.fail "Timeout while expecting: $expectations"})
	}

	String expect(Object expectations) {
		return expect(expectations, timeout)
	}

	void abort() {
		outputSessions.each {OutputSession it -> it.abort()}
		inputSessions.each {InputSession it -> it.abort()}
	}

	void close() throws java.io.IOException {
		closed = true
		outputSessions.each {OutputSession it -> it.close()}
		inputSessions.each {InputSession it -> it.close()}
	}
}

/*
def workingDir = "C:\somedir"
try {
    Process submitProcess = "cmd /K setEnv.bat".execute(null, new File(workingDir))
    ExpectSession session = new ExpectSession(submitProcess)
    session.expect (["${workingDir}>":                                                            {session.send("submit\n")}  ], 10*1000)
    session.expect (['Submit new activities? (yes/no) [YES]:':                                           {session.send("\n")}        ], 60*1000)
    session.expect (['- SET TO ONHOLD? (if this submit is waiting for another submit) (yes/no) [NO]:':   {session.send("\n")}        ], 20*1000)
    session.expect (['No changes since last baseline. You may have already baseline created.':           {}                          ], 20*1000)
    session.expect (['Is this OK? (yes/no) [YES]:':                                                      {session.send("\n")}        ], 20*1000)
    session.expect (['This program succeeded.':                                                          {}                          ], 60*1000)
    session.expect (["${workingDir}>":                                                            {session.send("exit\n")}    ], 10*1000)
    submitProcess.waitForOrKill(20000)
} catch (Exception e) {
    error e.message
    return false
}
return true
*/