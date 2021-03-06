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
package bitronix.tm.timer;

/**
 * Thrown when an error occurs during the execution of a task.
 *
 * @author Ludovic Orban
 */
public class TaskException
		extends Exception
{
	/**
	 * Constructor TaskException creates a new TaskException instance.
	 *
	 * @param message
	 * 		of type String
	 * @param cause
	 * 		of type Throwable
	 */
	public TaskException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
