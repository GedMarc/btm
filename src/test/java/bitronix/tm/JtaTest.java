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
package bitronix.tm;

import bitronix.tm.mock.resource.jdbc.MockDriver;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import bitronix.tm.resource.jdbc.lrc.LrcXADataSource;
import bitronix.tm.utils.DefaultExceptionAnalyzer;
import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import java.sql.Connection;

/**
 * @author Ludovic Orban
 */
public class JtaTest
		extends TestCase
{

	private final static Logger log = LoggerFactory.getLogger(JtaTest.class);

	private BitronixTransactionManager btm;

	@Override
	protected void setUp()
	{
		TransactionManagerServices.getConfiguration()
		                          .setGracefulShutdownInterval(1);
		TransactionManagerServices.getConfiguration()
		                          .setExceptionAnalyzer(DefaultExceptionAnalyzer.class.getName());
		btm = TransactionManagerServices.getTransactionManager();
	}

	@Override
	protected void tearDown()
	{
		btm.shutdown();
	}

	public void testTransactionManagerGetTransaction() throws Exception
	{
		assertNull(btm.getTransaction());

		btm.begin();
		assertNotNull(btm.getTransaction());

		btm.commit();
		assertNull(btm.getTransaction());

		btm.begin();
		assertNotNull(btm.getTransaction());

		btm.rollback();
	}

	// this test also helps verifying MDC support but logs have to be manually checked
	public void testSuspendResume() throws Exception
	{
		log.info("test starts");
		btm.begin();
		log.info("tx begun");
		Transaction tx = btm.suspend();
		log.info("tx suspended");
		btm.resume(tx);
		log.info("tx resumed");
		btm.rollback();
		log.info("test over");
	}

	public void testTimeout() throws Exception
	{
		btm.setTransactionTimeout(1);
		btm.begin();
		CountingSynchronization sync = new CountingSynchronization();
		btm.getTransaction()
		   .registerSynchronization(sync);

		Thread.sleep(2000);
		assertEquals(Status.STATUS_MARKED_ROLLBACK, btm.getTransaction()
		                                               .getStatus());

		try
		{
			btm.commit();
			fail("commit should have thrown an RollbackException");
		}
		catch (RollbackException ex)
		{
			assertEquals("transaction timed out and has been rolled back", ex.getMessage());
		}
		assertEquals(1, sync.beforeCount);
		assertEquals(1, sync.afterCount);
	}

	public void testMarkedRollback() throws Exception
	{
		btm.begin();
		CountingSynchronization sync = new CountingSynchronization();
		btm.getTransaction()
		   .registerSynchronization(sync);
		btm.setRollbackOnly();

		assertEquals(Status.STATUS_MARKED_ROLLBACK, btm.getTransaction()
		                                               .getStatus());

		try
		{
			btm.commit();
			fail("commit should have thrown an RollbackException");
		}
		catch (RollbackException ex)
		{
			assertEquals("transaction was marked as rollback only and has been rolled back", ex.getMessage());
		}
		assertEquals(1, sync.beforeCount);
		assertEquals(1, sync.afterCount);
	}

	public void testRecycleAfterSuspend() throws Exception
	{
		PoolingDataSource pds = new PoolingDataSource();
		pds.setClassName(LrcXADataSource.class.getName());
		pds.setUniqueName("lrc-pds");
		pds.setMaxPoolSize(2);
		pds.getDriverProperties()
		   .setProperty("driverClassName", MockDriver.class.getName());
		pds.init();

		btm.begin();

		Connection c1 = pds.getConnection();
		c1.createStatement();
		c1.close();

		Transaction tx = btm.suspend();

		btm.begin();

		Connection c11 = pds.getConnection();
		c11.createStatement();
		c11.close();

		btm.commit();


		btm.resume(tx);

		Connection c2 = pds.getConnection();
		c2.createStatement();
		c2.close();

		btm.commit();

		pds.close();
	}

	public void testTransactionContextCleanup() throws Exception
	{
		assertEquals(Status.STATUS_NO_TRANSACTION, btm.getStatus());

		btm.begin();
		assertEquals(Status.STATUS_ACTIVE, btm.getStatus());

		Transaction tx = btm.getTransaction();

		// commit on a different thread
		Thread t = new Thread(() ->
		                      {
			                      try
			                      {
				                      tx.commit();
			                      }
			                      catch (Exception ex)
			                      {
				                      ex.printStackTrace();
				                      fail();
			                      }
		                      });

		t.start();
		t.join();

		assertNull(btm.getTransaction());
	}

	public void testBeforeCompletionAddsExtraSynchronizationInDifferentPriority() throws Exception
	{
		btm.begin();

		btm.getCurrentTransaction()
		   .getSynchronizationScheduler()
		   .add(new SynchronizationRegisteringSynchronization(btm.getCurrentTransaction()), 5);

		btm.commit();
	}

	public void testDebugZeroResourceTransactionDisabled() throws Exception
	{
		btm.begin();
		assertNull("Activation stack trace must not be available by default.", btm.getCurrentTransaction()
		                                                                          .getActivationStackTrace());
		btm.commit();
	}

	public void testDebugZeroResourceTransaction() throws Exception
	{
		btm.shutdown(); // necessary to change the configuration
		TransactionManagerServices.getConfiguration()
		                          .setDebugZeroResourceTransaction(true);
		btm = TransactionManagerServices.getTransactionManager();

		btm.begin();
		assertNotNull("Activation stack trace must be available.", btm.getCurrentTransaction()
		                                                              .getActivationStackTrace());
		btm.commit();
	}

	public void testBeforeCompletionRuntimeExceptionRethrown() throws Exception
	{
		btm.begin();

		btm.getTransaction()
		   .registerSynchronization(new Synchronization()
		   {
			   @Override
			   public void beforeCompletion()
			   {
				   throw new RuntimeException("beforeCompletion failure");
			   }

			   @Override
			   public void afterCompletion(int i)
			   {
			   }
		   });

		try
		{
			btm.commit();
			fail("expected runtime exception");
		}
		catch (RollbackException ex)
		{
			assertEquals(RuntimeException.class, ex.getCause()
			                                       .getClass());
			assertEquals("beforeCompletion failure", ex.getCause()
			                                           .getMessage());
		}

		btm.begin();
		btm.commit();
	}

	private class SynchronizationRegisteringSynchronization
			implements Synchronization
	{

		private BitronixTransaction transaction;

		public SynchronizationRegisteringSynchronization(BitronixTransaction transaction)
		{
			this.transaction = transaction;
		}

		@Override
		public void beforeCompletion()
		{
			try
			{
				transaction.getSynchronizationScheduler()
				           .add(new Synchronization()
				           {

					           @Override
					           public void beforeCompletion()
					           {
					           }

					           @Override
					           public void afterCompletion(int i)
					           {
					           }
				           }, 10);
			}
			catch (Exception e)
			{
				fail("unexpected exception: " + e);
			}
		}

		@Override
		public void afterCompletion(int i)
		{
		}
	}

	private class CountingSynchronization
			implements Synchronization
	{
		public int beforeCount = 0;
		public int afterCount = 0;

		@Override
		public void beforeCompletion()
		{
			beforeCount++;
		}

		@Override
		public void afterCompletion(int i)
		{
			afterCount++;
		}
	}

}
