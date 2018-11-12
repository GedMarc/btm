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
package bitronix.tm.utils;

import bitronix.tm.Configuration;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.internal.LogDebugCheck;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;

/**
 * JMX facade used to (un)register any JMX enabled instances.
 * <p>
 * In case there is no JMX implementation available, calling methods of this class have no effect.
 * JMX registrations may be synchronous or asynchronous using a work-queue and worker thread.
 * The latter enables higher throughput by avoiding the registration of very short lived instances and
 * by that fact the JMX registrations can work on uncontended thread synchronization.
 *
 * @author Ludovic Orban
 * @author Juergen Kellerer
 */
public final class ManagementRegistrar
{

	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ManagementRegistrar.class.toString());
	private static final MBeanServer mbeanServer;
	private static final Queue<ManagementCommand> commandQueue;

	static
	{
		boolean enableJmx = !TransactionManagerServices.getConfiguration()
		                                               .isDisableJmx();

		if (enableJmx)
		{
			mbeanServer = ManagementFactory.getPlatformMBeanServer();
		}
		else
		{
			mbeanServer = null;
		}
	}

	static
	{
		Configuration configuration = TransactionManagerServices.getConfiguration();
		commandQueue = mbeanServer == null || configuration.isSynchronousJmxRegistration() ? null :
		               new ArrayBlockingQueue<>(1024);

		if (commandQueue != null)
		{
			new Thread()
			{
				{
					setName("bitronix-async-jmx-worker");
					setDaemon(true);
				}

				/**
				 * Method run ...
				 */
				@Override
				public void run()
				{
					while (!isInterrupted())
					{
						try
						{
							normalizeAndRunQueuedCommands();
							Thread.sleep(250); // sampling interval
						}
						catch (InterruptedException ex)
						{
							return;
						}
						catch (Exception ex)
						{
							log.log(Level.SEVERE, "an unexpected error occurred in JMX asynchronous registration code", ex);
						}
					}
				}
			}.start();
		}
	}

	static
	{
		if (mbeanServer != null)
		{
			if (LogDebugCheck.isDebugEnabled())
			{
				log.finer("Enabled JMX with MBeanServer " + mbeanServer + "; MBean registration is '" + (commandQueue == null ? "synchronous" : "asynchronous") + "'.");
			}
		}
		else
		{
			if (LogDebugCheck.isDebugEnabled())
			{
				log.finer("JMX support is disabled.");
			}
		}
	}

	/**
	 * Constructor ManagementRegistrar creates a new ManagementRegistrar instance.
	 */
	private ManagementRegistrar()
	{
	}

	/**
	 * Replace characters considered illegal in a management object's name.
	 *
	 * @param name
	 * 		the name to work on.
	 *
	 * @return a fully valid name where all invalid characters have been replaced with '_'.
	 */
	public static String makeValidName(String name)
	{
		return name.replaceAll("[\\:\\,\\=,\\.]", "_");
	}

	/**
	 * Register the specified management object.
	 *
	 * @param name
	 * 		the name of the object.
	 * @param obj
	 * 		the management object.
	 */
	public static void register(String name, Object obj)
	{
		if (mbeanServer == null)
		{
			return;
		}

		runOrEnqueueCommand(new ManagementRegisterCommand(name, obj));
	}

	/**
	 * Method runOrEnqueueCommand ...
	 *
	 * @param command
	 * 		of type ManagementCommand
	 */
	private static void runOrEnqueueCommand(ManagementCommand command)
	{
		if (commandQueue == null)
		{
			command.run();
		}
		else
		{
			// Try to enqueue the command unless the queue is full.
			// Recover from a full queue by running already queued commands first to protect the async. implementation
			// from being vulnerable to DOS attacks.
			while (!commandQueue.offer(command))
			{
				normalizeAndRunQueuedCommands();
			}
		}
	}

	/**
	 * Method normalizeAndRunQueuedCommands ...
	 */
	static void normalizeAndRunQueuedCommands()
	{
		if (commandQueue == null)
		{
			return;
		}

		// Synchronizing on commandQueue to ensure that even if 2 threads try to poll, only one can process the commands
		// that were scheduled at a time (happens if queue is full or during unit tests).
		// The latter is important to ensure that the command calling order is kept intact as parallel polling would destroy it.

		synchronized (commandQueue)
		{
			Map<String, ManagementCommand> mappedCommands = new LinkedHashMap<>(commandQueue.size());

			ManagementCommand command;
			while ((command = commandQueue.poll()) != null)
			{
				String name = command.getName();
				ManagementCommand previousCommand = mappedCommands.put(name, command);

				if (previousCommand instanceof ManagementRegisterCommand)
				{
					// Avoid that we have unbound un-register commands in the work queue.
					if (command instanceof ManagementUnregisterCommand && !((ManagementRegisterCommand) previousCommand).isReplace())
					{
						mappedCommands.remove(name);
					}
				}
				else if (previousCommand instanceof ManagementUnregisterCommand)
				{
					// We already have this MBean, flagging it for replacement.
					if (command instanceof ManagementRegisterCommand)
					{
						((ManagementRegisterCommand) command).setReplace(true);
					}
				}
			}

			for (ManagementCommand c : mappedCommands.values())
			{
				c.run();
			}
		}
	}

	/**
	 * Unregister the management object with the specified name.
	 *
	 * @param name
	 * 		the name of the object.
	 */
	public static void unregister(String name)
	{
		if (mbeanServer == null)
		{
			return;
		}

		runOrEnqueueCommand(new ManagementUnregisterCommand(name));
	}

	/**
	 * Registers the given instance within the JMX environment.
	 */
	private static class ManagementRegisterCommand
			extends ManagementCommand
	{

		private final WeakReference<Object> instance;
		private boolean replace;

		/**
		 * Constructor ManagementRegisterCommand creates a new ManagementRegisterCommand instance.
		 *
		 * @param name
		 * 		of type String
		 * @param instance
		 * 		of type Object
		 */
		ManagementRegisterCommand(String name, Object instance)
		{
			super(name);
			// Using a WeakReference to avoid holding hard refs on instances that may already be obsolete.
			this.instance = new WeakReference<>(instance);
		}

		/**
		 * Method isReplace returns the replace of this ManagementRegisterCommand object.
		 *
		 * @return the replace (type boolean) of this ManagementRegisterCommand object.
		 */
		boolean isReplace()
		{
			return replace;
		}

		/**
		 * Method setReplace sets the replace of this ManagementRegisterCommand object.
		 *
		 * @param replace
		 * 		the replace of this ManagementRegisterCommand object.
		 */
		void setReplace(boolean replace)
		{
			this.replace = replace;
		}

		/**
		 * Method runCommand ...
		 *
		 * @throws Exception
		 * 		when
		 */
		@Override
		protected void runCommand() throws Exception
		{
			Object object = instance.get();
			if (object != null)
			{
				ObjectName objectName = new ObjectName(name);
				if (replace && mbeanServer.isRegistered(objectName))
				{
					mbeanServer.unregisterMBean(objectName);
				}
				mbeanServer.registerMBean(object, objectName);
			}
		}
	}


	/**
	 * Unregisters the given instance within the JMX environment.
	 */
	private static class ManagementUnregisterCommand
			extends ManagementCommand
	{
		/**
		 * Constructor ManagementUnregisterCommand creates a new ManagementUnregisterCommand instance.
		 *
		 * @param name
		 * 		of type String
		 */
		ManagementUnregisterCommand(String name)
		{
			super(name);
		}

		/**
		 * Method runCommand ...
		 *
		 * @throws Exception
		 * 		when
		 */
		@Override
		protected void runCommand() throws Exception
		{
			try
			{
				mbeanServer.unregisterMBean(new ObjectName(name));
			}
			catch (InstanceNotFoundException e)
			{
				if (LogDebugCheck.isDebugEnabled())
				{
					log.finer("Failed to unregister the JMX instance of name '" + name + "' as it doesn't exist.");
				}
			}
		}
	}


	/**
	 * Base class for management related commands.
	 */
	private static abstract class ManagementCommand
			implements Runnable
	{

		final String name;

		/**
		 * Constructor ManagementCommand creates a new ManagementCommand instance.
		 *
		 * @param name
		 * 		of type String
		 */
		protected ManagementCommand(String name)
		{
			this.name = name;
		}

		/**
		 * Method getName returns the name of this ManagementCommand object.
		 *
		 * @return the name (type String) of this ManagementCommand object.
		 */
		public String getName()
		{
			return name;
		}

		/**
		 * Method run ...
		 */
		@Override
		public final void run()
		{
			try
			{
				if (LogDebugCheck.isDebugEnabled())
				{
					log.finer("Calling " + getClass().getSimpleName() + " on object with name " + name);
				}
				runCommand();
			}
			catch (Exception ex)
			{
				log.log(Level.WARNING, "Cannot execute " + getClass().getSimpleName() + " on object with name " + name, ex);
			}
		}

		/**
		 * Method runCommand ...
		 *
		 * @throws Exception
		 * 		when
		 */
		protected abstract void runCommand() throws Exception;

		/**
		 * Method toString ...
		 *
		 * @return String
		 */
		@Override
		public String toString()
		{
			return getClass().getSimpleName() + "{" +
			       "name='" + name + '\'' +
			       '}';
		}
	}
}
