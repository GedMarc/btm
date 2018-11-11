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
package bitronix.tm.mock.events;

import javax.transaction.xa.Xid;

/**
 * @author Ludovic Orban
 */
public class XAResourceCommitEvent
		extends XAEvent
{

	private boolean onePhase;

	/**
	 * Constructor XAResourceCommitEvent creates a new XAResourceCommitEvent instance.
	 *
	 * @param source
	 * 		of type Object
	 * @param xid
	 * 		of type Xid
	 * @param onePhase
	 * 		of type boolean
	 */
	public XAResourceCommitEvent(Object source, Xid xid, boolean onePhase)
	{
		super(source, xid);
		this.onePhase = onePhase;
	}

	/**
	 * Constructor XAResourceCommitEvent creates a new XAResourceCommitEvent instance.
	 *
	 * @param source
	 * 		of type Object
	 * @param ex
	 * 		of type Exception
	 * @param xid
	 * 		of type Xid
	 * @param onePhase
	 * 		of type boolean
	 */
	public XAResourceCommitEvent(Object source, Exception ex, Xid xid, boolean onePhase)
	{
		super(source, ex, xid);
		this.onePhase = onePhase;
	}

	/**
	 * Method isOnePhase returns the onePhase of this XAResourceCommitEvent object.
	 *
	 * @return the onePhase (type boolean) of this XAResourceCommitEvent object.
	 */
	public boolean isOnePhase()
	{
		return onePhase;
	}

	/**
	 * Method toString ...
	 *
	 * @return String
	 */
	@Override
	public String toString()
	{
		return "XAResourceCommitEvent at " + getTimestamp() + " with onePhase=" + onePhase +
		       (getException() != null ? " and " + getException().toString() : "" + " on " + getXid());
	}
}
