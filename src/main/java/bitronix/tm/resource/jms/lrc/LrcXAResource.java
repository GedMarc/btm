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
package bitronix.tm.resource.jms.lrc;

import bitronix.tm.internal.BitronixXAException;
import bitronix.tm.internal.LogDebugCheck;
import bitronix.tm.utils.Decoder;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * XAResource implementation for a non-XA JMS connection emulating XA with Last Resource Commit.
 * <p>The XA protocol flow is implemented by this state machine:</p>
 * <pre>
 * NO_TX
 *   |
 *   | start(TMNOFLAGS)
 *   |
 *   |       end(TMFAIL)
 * STARTED -------------- NO_TX
 *   |
 *   | end(TMSUCCESS)
 *   |
 *   |    start(TMJOIN)
 * ENDED ---------------- STARTED
 *   |\
 *   | \  commit (one phase)
 *   |  ----------------- NO_TX
 *   |
 *   | prepare()
 *   |
 *   |       commit() or
 *   |       rollback()
 * PREPARED ------------- NO_TX
 * </pre>
 * {@link XAResource#TMSUSPEND} and {@link XAResource#TMRESUME} are not supported.
 *
 * @author Ludovic Orban
 */
public class LrcXAResource
		implements XAResource
{

	public static final int NO_TX = 0;
	public static final int STARTED = 1;
	public static final int ENDED = 2;
	public static final int PREPARED = 3;
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LrcXAResource.class.toString());
	private static final String XID_CANT_BE_NULL = "XID cannot be null";
	private static final String RESOURCE_NOT_STARTED = "resource never started on XID ";
	private static final String RESOURCE_NEVER_ENDED = "resource never ended on XID ";
	private static final String XID_NEVER_ENDED = ", XID=";
	private static final String RESOURCE_ALREADY_STARTED = "resource already started on XID ";
	private static final String FLAG_EQUALS = ", flag=";

	private final Session session;
	private volatile Xid xid;
	private volatile int state = NO_TX;

	/**
	 * Constructor LrcXAResource creates a new LrcXAResource instance.
	 *
	 * @param session
	 * 		of type Session
	 */
	public LrcXAResource(Session session)
	{
		this.session = session;
	}


	/**
	 * Method getState returns the state of this LrcXAResource object.
	 *
	 * @return the state (type int) of this LrcXAResource object.
	 */
	public int getState()
	{
		return state;
	}

	/**
	 * Method commit ...
	 *
	 * @param xid
	 * 		of type Xid
	 * @param onePhase
	 * 		of type boolean
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public void commit(Xid xid, boolean onePhase) throws XAException
	{
		if (xid == null)
		{
			throw new BitronixXAException(XID_CANT_BE_NULL, XAException.XAER_INVAL);
		}

		if (state == NO_TX)
		{
			throw new BitronixXAException(RESOURCE_NOT_STARTED + xid, XAException.XAER_PROTO);
		}
		else if (state == STARTED)
		{
			throw new BitronixXAException(RESOURCE_NEVER_ENDED + xid, XAException.XAER_PROTO);
		}
		else if (state == ENDED)
		{
			if (onePhase)
			{
				if (LogDebugCheck.isDebugEnabled())
				{
					log.finer("OK to commit with 1PC, old state=" + xlatedState() + XID_NEVER_ENDED + xid);
				}
				try
				{
					session.commit();
				}
				catch (JMSException ex)
				{
					throw new BitronixXAException("error committing (one phase) non-XA resource", XAException.XAER_RMERR, ex);
				}
			}
			else
			{
				throw new BitronixXAException("resource never prepared on XID " + xid, XAException.XAER_PROTO);
			}
		}
		else if (state == PREPARED)
		{
			if (!onePhase)
			{
				if (this.xid.equals(xid))
				{
					if (LogDebugCheck.isDebugEnabled())
					{
						log.finer("OK to commit, old state=" + xlatedState() + XID_NEVER_ENDED + xid);
					}
				}
				else
				{
					throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid + " - cannot commit it on another XID " + xid, XAException.XAER_PROTO);
				}
			}
			else
			{
				throw new BitronixXAException("cannot commit in one phase as resource has been prepared on XID " + xid, XAException.XAER_PROTO);
			}
		}

		this.state = NO_TX;
		this.xid = null;
	}

	/**
	 * Method end ...
	 *
	 * @param xid
	 * 		of type Xid
	 * @param flag
	 * 		of type int
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public void end(Xid xid, int flag) throws XAException
	{
		if (flag != XAResource.TMSUCCESS && flag != XAResource.TMFAIL)
		{
			throw new BitronixXAException("unsupported end flag " + Decoder.decodeXAResourceFlag(flag), XAException.XAER_RMERR);
		}
		if (xid == null)
		{
			throw new BitronixXAException(XID_CANT_BE_NULL, XAException.XAER_INVAL);
		}

		if (state == NO_TX)
		{
			throw new BitronixXAException(RESOURCE_NOT_STARTED + xid, XAException.XAER_PROTO);
		}
		else if (state == STARTED)
		{
			if (this.xid.equals(xid))
			{
				if (LogDebugCheck.isDebugEnabled())
				{
					log.finer("OK to end, old state=" + xlatedState() + XID_NEVER_ENDED + xid + FLAG_EQUALS + Decoder.decodeXAResourceFlag(flag));
				}
			}
			else
			{
				throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid + " - cannot end it on another XID " + xid, XAException.XAER_PROTO);
			}
		}
		else if (state == ENDED)
		{
			throw new BitronixXAException("resource already ended on XID " + xid, XAException.XAER_PROTO);
		}
		else if (state == PREPARED)
		{
			throw new BitronixXAException("cannot end, resource already prepared on XID " + xid, XAException.XAER_PROTO);
		}

		if (flag == XAResource.TMFAIL)
		{
			try
			{
				session.rollback();
				state = NO_TX;
				this.xid = null;
				return;
			}
			catch (JMSException ex)
			{
				throw new BitronixXAException("error rolling back resource on end", XAException.XAER_RMERR, ex);
			}
		}

		this.state = ENDED;
	}

	/**
	 * Method forget ...
	 *
	 * @param xid
	 * 		of type Xid
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public void forget(Xid xid) throws XAException
	{
		//Nothing required
	}

	/**
	 * Method getTransactionTimeout returns the transactionTimeout of this LrcXAResource object.
	 *
	 * @return the transactionTimeout (type int) of this LrcXAResource object.
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public int getTransactionTimeout() throws XAException
	{
		return 0;
	}

	/**
	 * Method isSameRM ...
	 *
	 * @param xaResource
	 * 		of type XAResource
	 *
	 * @return boolean
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public boolean isSameRM(XAResource xaResource) throws XAException
	{
		return xaResource == this;
	}

	/**
	 * Method prepare ...
	 *
	 * @param xid
	 * 		of type Xid
	 *
	 * @return int
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public int prepare(Xid xid) throws XAException
	{
		if (xid == null)
		{
			throw new BitronixXAException(XID_CANT_BE_NULL, XAException.XAER_INVAL);
		}

		if (state == NO_TX)
		{
			throw new BitronixXAException(RESOURCE_NOT_STARTED + xid, XAException.XAER_PROTO);
		}
		else if (state == STARTED)
		{
			throw new BitronixXAException(RESOURCE_NEVER_ENDED + xid, XAException.XAER_PROTO);
		}
		else if (state == ENDED)
		{
			if (this.xid.equals(xid))
			{
				if (LogDebugCheck.isDebugEnabled())
				{
					log.finer("OK to prepare, old state=" + xlatedState() + XID_NEVER_ENDED + xid);
				}
			}
			else
			{
				throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid + " - cannot prepare it on another XID " + xid, XAException.XAER_PROTO);
			}
		}
		else if (state == PREPARED)
		{
			throw new BitronixXAException("resource already prepared on XID " + this.xid, XAException.XAER_PROTO);
		}

		try
		{
			session.commit();
			this.state = PREPARED;
			return XAResource.XA_OK;
		}
		catch (JMSException ex)
		{
			throw new BitronixXAException("error preparing non-XA resource", XAException.XAER_RMERR, ex);
		}
	}

	/**
	 * Method recover ...
	 *
	 * @param flags
	 * 		of type int
	 *
	 * @return Xid[]
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public Xid[] recover(int flags) throws XAException
	{
		return new Xid[0];
	}

	/**
	 * Method rollback ...
	 *
	 * @param xid
	 * 		of type Xid
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public void rollback(Xid xid) throws XAException
	{
		if (xid == null)
		{
			throw new BitronixXAException(XID_CANT_BE_NULL, XAException.XAER_INVAL);
		}

		if (state == NO_TX)
		{
			throw new BitronixXAException(RESOURCE_NOT_STARTED + xid, XAException.XAER_PROTO);
		}
		else if (state == STARTED)
		{
			throw new BitronixXAException(RESOURCE_NEVER_ENDED + xid, XAException.XAER_PROTO);
		}
		else if (state == ENDED)
		{
			if (this.xid.equals(xid))
			{
				if (LogDebugCheck.isDebugEnabled())
				{
					log.finer("OK to rollback, old state=" + xlatedState() + XID_NEVER_ENDED + xid);
				}
			}
			else
			{
				throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid + " - cannot roll it back on another XID " + xid, XAException.XAER_PROTO);
			}
		}
		else if (state == PREPARED)
		{
			this.state = NO_TX;
			throw new BitronixXAException("resource committed during prepare on XID " + this.xid, XAException.XA_HEURCOM);
		}

		try
		{
			session.rollback();
		}
		catch (JMSException ex)
		{
			throw new BitronixXAException("error preparing non-XA resource", XAException.XAER_RMERR, ex);
		}
		finally
		{
			this.state = NO_TX;
			this.xid = null;
		}
	}

	/**
	 * Method setTransactionTimeout ...
	 *
	 * @param seconds
	 * 		of type int
	 *
	 * @return boolean
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public boolean setTransactionTimeout(int seconds) throws XAException
	{
		return false;
	}

	/**
	 * Method start ...
	 *
	 * @param xid
	 * 		of type Xid
	 * @param flag
	 * 		of type int
	 *
	 * @throws XAException
	 * 		when
	 */
	@Override
	public void start(Xid xid, int flag) throws XAException
	{
		if (flag != XAResource.TMNOFLAGS && flag != XAResource.TMJOIN)
		{
			throw new BitronixXAException("unsupported start flag " + Decoder.decodeXAResourceFlag(flag), XAException.XAER_RMERR);
		}
		if (xid == null)
		{
			throw new BitronixXAException(XID_CANT_BE_NULL, XAException.XAER_INVAL);
		}

		if (state == NO_TX)
		{
			if (this.xid != null)
			{
				throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid, XAException.XAER_PROTO);
			}
			else
			{
				if (flag == XAResource.TMJOIN)
				{
					throw new BitronixXAException("resource not yet started", XAException.XAER_PROTO);
				}
				else
				{
					if (LogDebugCheck.isDebugEnabled())
					{
						log.finer("OK to start, old state=" + xlatedState() + XID_NEVER_ENDED + xid + FLAG_EQUALS + Decoder.decodeXAResourceFlag(flag));
					}
					this.xid = xid;
				}
			}
		}
		else if (state == STARTED)
		{
			throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid, XAException.XAER_PROTO);
		}
		else if (state == ENDED)
		{
			if (flag == XAResource.TMNOFLAGS)
			{
				throw new BitronixXAException("resource already registered XID " + this.xid, XAException.XAER_DUPID);
			}
			else
			{
				if (xid.equals(this.xid))
				{
					if (LogDebugCheck.isDebugEnabled())
					{
						log.finer("OK to join, old state=" + xlatedState() + XID_NEVER_ENDED + xid + FLAG_EQUALS + Decoder.decodeXAResourceFlag(flag));
					}
				}
				else
				{
					throw new BitronixXAException(RESOURCE_ALREADY_STARTED + this.xid + " - cannot start it on more than one XID at a time", XAException.XAER_RMERR);
				}
			}
		}
		else if (state == PREPARED)
		{
			throw new BitronixXAException("resource already prepared on XID " + this.xid, XAException.XAER_PROTO);
		}

		this.state = STARTED;
	}

	/**
	 * Method xlatedState ...
	 *
	 * @return String
	 */
	private String xlatedState()
	{
		switch (state)
		{
			case NO_TX:
				return "NO_TX";
			case STARTED:
				return "STARTED";
			case ENDED:
				return "ENDED";
			case PREPARED:
				return "PREPARED";
			default:
				return "!invalid state (" + state + ")!";
		}
	}

	/**
	 * Method toString ...
	 *
	 * @return String
	 */
	@Override
	public String toString()
	{
		return "a JMS LrcXAResource in state " + xlatedState() + " of session " + session;
	}
}
