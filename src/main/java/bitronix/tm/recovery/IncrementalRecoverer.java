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
package bitronix.tm.recovery;

import bitronix.tm.BitronixXid;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.internal.LogDebugCheck;
import bitronix.tm.internal.XAResourceHolderState;
import bitronix.tm.journal.JournalRecord;
import bitronix.tm.resource.common.XAResourceProducer;
import bitronix.tm.utils.Decoder;
import bitronix.tm.utils.Uid;

import jakarta.transaction.Status;
import javax.transaction.xa.XAException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Incremental resource recoverer.
 *
 * @author Ludovic Orban
 */
public class IncrementalRecoverer
{

	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(IncrementalRecoverer.class.toString());
	private static final String FAILED_RESOURCE_STRING = "failed recovering resource ";

	private IncrementalRecoverer()
	{
		//No config required
	}

	/**
	 * Run incremental recovery on the specified resource.
	 *
	 * @param xaResourceProducer
	 * 		the resource to recover.
	 *
	 * @throws RecoveryException
	 * 		when an error preventing recovery happens.
	 */
	public static void recover(XAResourceProducer xaResourceProducer) throws RecoveryException
	{
		String uniqueName = xaResourceProducer.getUniqueName();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("start of incremental recovery on resource " + uniqueName);
		}

		try
		{
			XAResourceHolderState xaResourceHolderState = xaResourceProducer.startRecovery();
			boolean success = true;
			Set<BitronixXid> xids = RecoveryHelper.recover(xaResourceHolderState);
			if (LogDebugCheck.isDebugEnabled())
			{
				log.finer(xids.size() + " dangling transaction(s) found on resource");
			}
			Map<?, ?> danglingRecords = TransactionManagerServices.getJournal()
			                                                      .collectDanglingRecords();
			if (LogDebugCheck.isDebugEnabled())
			{
				log.finer(danglingRecords.size() + " dangling transaction(s) found in journal");
			}

			int commitCount = 0;
			int rollbackCount = 0;
			for (BitronixXid xid : xids)
			{
				Uid gtrid = xid.getGlobalTransactionIdUid();

				JournalRecord tlog = (JournalRecord) danglingRecords.get(gtrid);
				if (tlog != null)
				{
					if (LogDebugCheck.isDebugEnabled())
					{
						log.finer("committing " + xid);
					}
					success &= RecoveryHelper.commit(xaResourceHolderState, xid);
					updateJournal(xid.getGlobalTransactionIdUid(), uniqueName, Status.STATUS_COMMITTED);
					commitCount++;
				}
				else
				{
					if (LogDebugCheck.isDebugEnabled())
					{
						log.finer("rolling back " + xid);
					}
					success &= RecoveryHelper.rollback(xaResourceHolderState, xid);
					updateJournal(xid.getGlobalTransactionIdUid(), uniqueName, Status.STATUS_ROLLEDBACK);
					rollbackCount++;
				}
			}

			// if recovery isn't successful we don't mark the resource as failed: heuristics might have happened
			// but communication with the resouce is working.
			if (!success)
			{
				throw new RecoveryException("error recovering resource '" + uniqueName + "' due to an incompatible heuristic decision");
			}

			xaResourceProducer.setFailed(false);

			log.info("incremental recovery committed " + commitCount + " dangling transaction(s) and rolled back " + rollbackCount +
			         " aborted transaction(s) on resource [" + uniqueName + "]" +
			         ((TransactionManagerServices.getConfiguration()
			                                     .isCurrentNodeOnlyRecovery()) ? " (restricted to serverId '" + TransactionManagerServices.getConfiguration()
			                                                                                                                              .getServerId() + "')" : ""));

		}
		catch (XAException | IOException | RuntimeException ex)
		{
			xaResourceProducer.setFailed(true);
			throw new RecoveryException(FAILED_RESOURCE_STRING + uniqueName, ex);
		}
		catch (RecoveryException ex)
		{
			xaResourceProducer.setFailed(true);
			throw ex;
		}
		finally
		{
			xaResourceProducer.endRecovery();
			if (LogDebugCheck.isDebugEnabled())
			{
				log.finer("end of incremental recovery on resource " + uniqueName);
			}
		}
	}

	/**
	 * Method updateJournal ...
	 *
	 * @param gtrid
	 * 		of type Uid
	 * @param uniqueName
	 * 		of type String
	 * @param status
	 * 		of type int
	 *
	 * @throws IOException
	 * 		when
	 */
	private static void updateJournal(Uid gtrid, String uniqueName, int status) throws IOException
	{
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("updating journal, adding " + Decoder.decodeStatus(status) + " entry for [" + uniqueName + "] on GTRID [" + gtrid + "]");
		}
		Set<String> participatingUniqueNames = new HashSet<>();
		participatingUniqueNames.add(uniqueName);
		TransactionManagerServices.getJournal()
		                          .log(status, gtrid, participatingUniqueNames);
	}


}
