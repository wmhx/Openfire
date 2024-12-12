/*
 * Copyright (C) 2016 IgniteRealtime.org, 2018-2024 Ignite Realtime Foundation. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.user;

import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Delegate UserProvider operations among up to three configurable provider implementation classes.
 *
 * This class related to, but is distinct from {@link MappedUserProvider}. The Hybrid variant of the provider iterates
 * over providers, operating on the first applicable instance. The Mapped variant, however, maps each user to exactly
 * one provider.
 *
 * @author Marc Seeger
 * @author Chris Neasbitt
 * @author Tom Evans
 * @author Guus der Kinderen
 */
public class HybridUserProvider extends UserMultiProvider
{
    private static final Logger Log = LoggerFactory.getLogger( HybridUserProvider.class );

    private final List<UserProvider> userProviders = new ArrayList<>();

    public HybridUserProvider()
    {
        // Migrate user provider properties
        JiveGlobals.migrateProperty( "hybridUserProvider.primaryProvider.className" );
        JiveGlobals.migrateProperty( "hybridUserProvider.secondaryProvider.className" );
        JiveGlobals.migrateProperty( "hybridUserProvider.tertiaryProvider.className" );

        // Load primary, secondary, and tertiary user providers.
        final UserProvider primary = instantiate( "hybridUserProvider.primaryProvider.className" );
        if ( primary != null )
        {
            userProviders.add( primary );
        }
        final UserProvider secondary = instantiate( "hybridUserProvider.secondaryProvider.className" );
        if ( secondary != null )
        {
            userProviders.add( secondary );
        }
        final UserProvider tertiary = instantiate( "hybridUserProvider.tertiaryProvider.className" );
        if ( tertiary != null )
        {
            userProviders.add( tertiary );
        }

        // Verify that there's at least one provider available.
        if ( userProviders.isEmpty() )
        {
            Log.error( "At least one UserProvider must be specified via openfire.xml or the system properties!" );
        }
    }

    @Override
    protected List<UserProvider> getUserProviders()
    {
        return userProviders;
    }

    /**
     * Returns the first provider that contains the user, or the first provider that is not read-only when the user
     * does not exist in any provider.
     *
     * @param username the username (cannot be null or empty).
     * @return The user provider (never null)
     */
    @Override
    public UserProvider getUserProvider(String username)
    {
        UserProvider nonReadOnly = null;
        for (final UserProvider provider : getUserProviders())
        {
            try
            {
                provider.loadUser(username);
                return provider;
            }
            catch (UserNotFoundException unfe)
            {
                Log.debug( "User {} not found by UserProvider {}", username, provider.getClass().getName() );

                if (nonReadOnly == null && !provider.isReadOnly()) {
                    nonReadOnly = provider;
                }
            }
        }

        // User does not exist. Return a provider suitable for creating users.
        if ( nonReadOnly == null )
        {
            throw new UnsupportedOperationException();
        }

        return nonReadOnly;
    }

    /**
     * Loads a user from the first provider that contains the user.
     *
     * @param username the username (cannot be null or empty).
     * @return The user (never null).
     * @throws UserNotFoundException When none of the providers contains the user.
     */
    @Override
    public User loadUser( String username ) throws UserNotFoundException
    {
        // Override the implementation in the superclass to prevent obtaining the user twice.
        for (UserProvider provider : userProviders)
        {
            try {
                return provider.loadUser( username );
            } catch ( UserNotFoundException unfe ) {
                Log.debug("User {} not found by UserProvider {}", username, provider.getClass().getName());
            }
        }
        //if we get this far, no provider was able to load the user
        throw new UserNotFoundException();
    }
}

