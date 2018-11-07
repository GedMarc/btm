/*
 * Copyright (C) 2006-2013 Bitronix Software (http://www.bitronix.be)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bitronix.tm.journal;

import bitronix.tm.internal.LogDebugCheck;
import bitronix.tm.utils.Uid;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Set;

/**
 * Used to read {@link TransactionLogRecord} objects from a log file.
 *
 * @author Ludovic Orban
 */
public class TransactionLogCursor
{

	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(TransactionLogCursor.class.toString());

	// private final RandomAccessFile randomAccessFile;
	private final FileInputStream fis;
	private final FileChannel fileChannel;
	private final long endPosition;
	private final ByteBuffer page;
	private long currentPosition;

	/**
	 * Create a TransactionLogCursor that will read from the specified file.
	 * This opens a new read-only file descriptor.
	 *
	 * @param file
	 * 		the file to read logs from
	 *
	 * @throws IOException
	 * 		if an I/O error occurs.
	 */
	public TransactionLogCursor(File file) throws IOException
	{
		this.fis = new FileInputStream(file);
		this.fileChannel = fis.getChannel();
		this.page = ByteBuffer.allocate(8192);

		fileChannel.position(TransactionLogHeader.CURRENT_POSITION_HEADER);
		fileChannel.read(page);
		page.rewind();
		endPosition = page.getLong();
		currentPosition = TransactionLogHeader.CURRENT_POSITION_HEADER + 8L;
	}

	/**
	 * Fetch the next TransactionLogRecord from log, recalculating the CRC and checking it against the stored one.
	 * InvalidChecksumException is thrown if the check fails.
	 *
	 * @return the TransactionLogRecord or null if the end of the log file has been reached
	 *
	 * @throws IOException
	 * 		if an I/O error occurs.
	 */
	public TransactionLogRecord readLog() throws IOException
	{
		return readLog(false);
	}

	/**
	 * Fetch the next TransactionLogRecord from log.
	 *
	 * @param skipCrcCheck
	 * 		if set to false, the method will thow an InvalidChecksumException if the CRC on disk does
	 * 		not match the recalculated one. Otherwise, the CRC is not recalculated nor checked agains the stored one.
	 *
	 * @return the TransactionLogRecord or null if the end of the log file has been reached
	 *
	 * @throws IOException
	 * 		if an I/O error occurs.
	 */
	public TransactionLogRecord readLog(boolean skipCrcCheck) throws IOException
	{
		if (currentPosition >= endPosition)
		{
			if (LogDebugCheck.isDebugEnabled())
			{
				log.finer("end of transaction log file reached at " + currentPosition);
			}
			return null;
		}

		int status = page.getInt();
		// currentPosition += 4;
		int recordLength = page.getInt();
		// currentPosition += 4;
		currentPosition += 8;

		if (page.position() + recordLength + 8 > page.limit())
		{
			page.compact();
			fileChannel.read(page);
			page.rewind();
		}

		int endOfRecordPosition = page.position() + recordLength;
		if (currentPosition + recordLength > endPosition)
		{
			page.position(page.position() + recordLength);
			currentPosition += recordLength;
			throw new CorruptedTransactionLogException("corrupted log found at position " + currentPosition
			                                           + " (record terminator outside of file bounds: " + currentPosition + recordLength + " of "
			                                           + endPosition + ", recordLength: " + recordLength + ")");
		}

		int headerLength = page.getInt();
		// currentPosition += 4;
		long time = page.getLong();
		// currentPosition += 8;
		int sequenceNumber = page.getInt();
		// currentPosition += 4;
		int crc32 = page.getInt();
		// currentPosition += 4;
		byte gtridSize = page.get();
		// currentPosition += 1;
		currentPosition += 21;

		// check for log terminator
		page.mark();
		page.position(endOfRecordPosition - 4);
		int endCode = page.getInt();
		page.reset();
		if (endCode != TransactionLogAppender.END_RECORD)
		{
			throw new CorruptedTransactionLogException("corrupted log found at position " + currentPosition + " (no record terminator found)");
		}

		// check that GTRID is not too long
		if (4 + 8 + 4 + 4 + 1 + gtridSize > recordLength)
		{
			page.position(endOfRecordPosition);
			throw new CorruptedTransactionLogException("corrupted log found at position " + currentPosition
			                                           + " (GTRID size too long)");
		}

		byte[] gtridArray = new byte[gtridSize];
		page.get(gtridArray);
		currentPosition += gtridSize;
		Uid gtrid = new Uid(gtridArray);
		int uniqueNamesCount = page.getInt();
		currentPosition += 4;
		Set<String> uniqueNames = new HashSet<>();
		int currentReadCount = 4 + 8 + 4 + 4 + 1 + gtridSize + 4;

		for (int i = 0; i < uniqueNamesCount; i++)
		{
			int length = page.getShort();
			currentPosition += 2;

			// check that names aren't too long
			currentReadCount += 2 + length;
			if (currentReadCount > recordLength)
			{
				page.position(endOfRecordPosition);
				throw new CorruptedTransactionLogException("corrupted log found at position " + currentPosition
				                                           + " (unique names too long, " + (i + 1) + " out of " + uniqueNamesCount + ", length: " + length
				                                           + ", currentReadCount: " + currentReadCount + ", recordLength: " + recordLength + ")");
			}

			byte[] nameBytes = new byte[length];
			page.get(nameBytes);
			currentPosition += length;
			uniqueNames.add(new String(nameBytes, "US-ASCII"));
		}
		int cEndRecord = page.getInt();
		currentPosition += 4;

		TransactionLogRecord tlog = new TransactionLogRecord(status, recordLength, headerLength, time, sequenceNumber,
		                                                     crc32, gtrid, uniqueNames, cEndRecord);

		// check that CRC is okay
		if (!skipCrcCheck && !tlog.isCrc32Correct())
		{
			page.position(endOfRecordPosition);
			throw new CorruptedTransactionLogException("corrupted log found at position " + currentPosition
			                                           + "(invalid CRC, recorded: " + tlog.getCrc32() + ", calculated: " + tlog.calculateCrc32() + ")");
		}

		return tlog;
	}

	/**
	 * Close the cursor and the underlying file
	 *
	 * @throws IOException
	 * 		if an I/O error occurs.
	 */
	public void close() throws IOException
	{
		fis.close();
		fileChannel.close();
	}
}
