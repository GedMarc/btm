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

import junit.framework.TestCase;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;

/**
 * @author Ludovic Orban
 */
public class BitronixTransactionSynchronizationRegistryTest
		extends TestCase
{

	private BitronixTransactionManager btm;

	/**
	 * Method setUp ...
	 */
	@Override
	protected void setUp()
	{
		btm = TransactionManagerServices.getTransactionManager();
	}

	/**
	 * Method tearDown ...
	 *
	 * @throws Exception
	 * 		when
	 */
	@Override
	protected void tearDown() throws Exception
	{
		if (btm.getStatus() != Status.STATUS_NO_TRANSACTION)
		{
			btm.rollback();
		}
		btm.shutdown();
	}

	/**
	 * Method testMultiThreaded ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testMultiThreaded() throws Exception
	{
		TransactionSynchronizationRegistry reg = TransactionManagerServices.getTransactionSynchronizationRegistry();

		btm.begin();
		reg.putResource("1", "one");
		assertEquals("one", reg.getResource("1"));
		btm.commit();

		Thread t = new Thread(() ->
		                      {
			                      try
			                      {
				                      btm.begin();
				                      reg.putResource("1", "one");
				                      assertEquals("one", reg.getResource("1"));
				                      btm.commit();
			                      }
			                      catch (Exception e)
			                      {
				                      throw new RuntimeException(e);
			                      }
		                      });

		t.start();
		t.join();
	}

	/**
	 * Method testRegistryResources ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testRegistryResources() throws Exception
	{
		TransactionSynchronizationRegistry reg = TransactionManagerServices.getTransactionSynchronizationRegistry();

		try
		{
			reg.putResource("0", "zero");
			fail("expected IllegalStateException");
		}
		catch (IllegalStateException ex)
		{
			assertEquals("no transaction started on current thread", ex.getMessage());
		}

		btm.begin();
		reg.putResource("1", "one");
		assertEquals("one", reg.getResource("1"));
		btm.commit();

		try
		{
			reg.getResource("1");
			fail("expected IllegalStateException");
		}
		catch (Exception ex)
		{
			assertEquals("no transaction started on current thread", ex.getMessage());
		}

		btm.begin();
		assertNull(reg.getResource("1"));
		btm.commit();
	}


	/**
	 * Method testRegistrySynchronizations ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testRegistrySynchronizations() throws Exception
	{
		TransactionSynchronizationRegistry reg = TransactionManagerServices.getTransactionSynchronizationRegistry();

		CoutingSynchronization normalSync = new CoutingSynchronization();
		CoutingSynchronization interposedSync = new CoutingSynchronization();

		btm.begin();

		reg.registerInterposedSynchronization(interposedSync);
		btm.getCurrentTransaction()
		   .registerSynchronization(normalSync);

		btm.commit();

		assertTrue(normalSync.getBeforeTimestamp() < interposedSync.getBeforeTimestamp());
		assertTrue(interposedSync.getBeforeTimestamp() < normalSync.getAfterTimestamp());
		assertTrue(interposedSync.getAfterTimestamp() < normalSync.getAfterTimestamp());
	}

	private class CoutingSynchronization
			implements Synchronization
	{

		private long beforeTimestamp;
		private long afterTimestamp;

		/**
		 * Method getBeforeTimestamp returns the beforeTimestamp of this CoutingSynchronization object.
		 *
		 * @return the beforeTimestamp (type long) of this CoutingSynchronization object.
		 */
		public long getBeforeTimestamp()
		{
			return beforeTimestamp;
		}

		/**
		 * Method getAfterTimestamp returns the afterTimestamp of this CoutingSynchronization object.
		 *
		 * @return the afterTimestamp (type long) of this CoutingSynchronization object.
		 */
		public long getAfterTimestamp()
		{
			return afterTimestamp;
		}

		/**
		 * Method beforeCompletion ...
		 */
		@Override
		public void beforeCompletion()
		{
			beforeTimestamp = System.currentTimeMillis();
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				// ignore
			}
		}

		/**
		 * Method afterCompletion ...
		 *
		 * @param status
		 * 		of type int
		 */
		@Override
		public void afterCompletion(int status)
		{
			afterTimestamp = System.currentTimeMillis();
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				// ignore
			}
		}
	}

}
