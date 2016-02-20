package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.factory;
import static com.google.devrel.training.conference.service.OfyService.ofy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Named;

import com.googlecode.objectify.cmd.Query;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID,
		Constants.API_EXPLORER_CLIENT_ID }, description = "API for the Conference Central Backend application.")
public class ConferenceApi {

	/*
	 * Get the display name from the user's email. For example, if the email is
	 * lemoncake@example.com, then the display name becomes "lemoncake."
	 */
	private static String extractDefaultDisplayNameFromEmail(String email) {
		return email == null ? null : email.substring(0, email.indexOf("@"));
	}

	/**
	 * Creates or updates a Profile object associated with the given user
	 * object.
	 *
	 * @param user
	 *            A User object injected by the cloud endpoints.
	 * @param profileForm
	 *            A ProfileForm object sent from the client form.
	 * @return Profile object just created.
	 * @throws UnauthorizedException
	 *             when the User object is null.
	 */

	// Declare this method as a method available externally through Endpoints
	@ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
	// The request that invokes this method should provide data that
	// conforms to the fields defined in ProfileForm
	public Profile saveProfile(User user, ProfileForm pf) throws UnauthorizedException {

		String userId = null;
		String mainEmail = null;
		String displayName = "Your Name Here";
		TeeShirtSize teeShirtSize = TeeShirtSize.NOT_SPECIFIED;

		if (user == null)
			throw new UnauthorizedException("You have not logged in.");
		if (pf != null)
			teeShirtSize = pf.getTeeShirtSize() != null ? pf.getTeeShirtSize() : TeeShirtSize.NOT_SPECIFIED;
		if (pf != null && pf.getDisplayName() != null)
			displayName = pf.getDisplayName();

		userId = user.getUserId();
		mainEmail = user.getEmail();

		if (user.getEmail() != null
				&& (displayName == null /*
										 * || displayName.equals(
										 * "Your Name Here")
										 */))
			displayName = extractDefaultDisplayNameFromEmail(mainEmail);

		Profile profile = getProfile(user);
		if (profile == null)
			profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
		else
			profile.update(displayName, teeShirtSize);

		ofy().save().entity(profile).now();

		// Return the profile
		return profile;
	}

	/**
	 * Returns a Profile object associated with the given user object. The cloud
	 * endpoints system automatically inject the User object.
	 *
	 * @param user
	 *            A User object injected by the cloud endpoints.
	 * @return Profile object.
	 * @throws UnauthorizedException
	 *             when the User object is null.
	 */
	@ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
	public Profile getProfile(final User user) throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}
		String userId = user.getUserId();
		Key<Profile> key = Key.create(Profile.class, userId);
		Profile profile = ofy().load().key(key).now();
		return profile;
	}

	/**
	 * Gets the Profile entity for the current user or creates it if it doesn't
	 * exist
	 * 
	 * @param user
	 * @return user's Profile
	 */
	private static Profile getProfileFromUser(User user) {
		// First fetch the user's Profile from the datastore.
		Profile profile = ofy().load().key(Key.create(Profile.class, user.getUserId())).now();
		if (profile == null) {
			// Create a new Profile if it doesn't exist.
			// Use default displayName and teeShirtSize
			String email = user.getEmail();
			profile = new Profile(user.getUserId(), extractDefaultDisplayNameFromEmail(email), email,
					TeeShirtSize.NOT_SPECIFIED);
		}
		return profile;
	}

	/**
	 * Creates a new Conference object and stores it to the datastore.
	 *
	 * @param user
	 *            A user who invokes this method, null when the user is not
	 *            signed in.
	 * @param conferenceForm
	 *            A ConferenceForm object representing user's inputs.
	 * @return A newly created Conference Object.
	 * @throws UnauthorizedException
	 *             when the user is not signed in.
	 */
	@ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
	public Conference createConference(final User user, final ConferenceForm conferenceForm)
			throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}
		String userId = user.getUserId();

		Key<Profile> profileKey = Key.create(Profile.class, user.getUserId());
		final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);

		final long conferenceId = conferenceKey.getId();
		Profile profile = getProfileFromUser(user);
		Conference conference = ofy().load().key(Key.create(profileKey, Conference.class, conferenceId)).now();
		if (conference == null)
			conference = new Conference(conferenceId, userId, conferenceForm);
		else
			conference.updateWithConferenceForm(conferenceForm);
		ofy().save().entities(profile, conference).now();

		return conference;
	}

	@ApiMethod(name = "queryConferences", path = "queryConferences", httpMethod = HttpMethod.POST)
	public List<Conference> queryConferences(ConferenceQueryForm conferenceQueryForm) {
		Iterable<Conference> conferenceIterable = conferenceQueryForm.getQuery();
		List<Conference> result = new ArrayList<>(0);
		List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
		for (Conference conference : conferenceIterable) {
			organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
			result.add(conference);
		}
		// To avoid separate datastore gets for each Conference, pre-fetch the
		// Profiles.
		ofy().load().keys(organizersKeyList);
		return result;
	}

	@ApiMethod(name = "getConferencesCreated", path = "getConferencesCreated", httpMethod = HttpMethod.POST)
	public List<Conference> getConferencesCreated(final User user) throws UnauthorizedException {
		if (user == null)
			throw new UnauthorizedException("Authorization required.");
		Profile profile = getProfileFromUser(user);
		if (profile != null) {
			Query query = ofy().load().type(Conference.class).ancestor(profile);
			return query.list();
		}
		return new ArrayList<Conference>(0);
	}

	@ApiMethod(name = "getConferencesFiltered", path = "getConferencesFiltered", httpMethod = HttpMethod.POST)
	public List<Conference> getConferencesFiltered() {
		Query query = ofy().load().type(Conference.class);
		query = query.filter("maxAttendees >", 10);
		query = query.filter("city =", "London");
		query = query.filter("topics =", "Web Technologies");
		query = query.filter("month =", 1).order("maxAttendees").order("name");
		return query.list();
	}

	/**
	 * Returns a Conference object with the given conferenceId.
	 *
	 * @param websafeConferenceKey
	 *            The String representation of the Conference Key.
	 * @return a Conference object with the given conferenceId.
	 * @throws NotFoundException
	 *             when there is no Conference with the given conferenceId.
	 */
	@ApiMethod(name = "getConference", path = "conference/{websafeConferenceKey}", httpMethod = HttpMethod.GET)
	public Conference getConference(@Named("websafeConferenceKey") final String websafeConferenceKey)
			throws NotFoundException {
		Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
		Conference conference = ofy().load().key(conferenceKey).now();
		if (conference == null) {
			throw new NotFoundException("No Conference found with key: " + websafeConferenceKey);
		}
		return conference;
	}

	/**
	 * Just a wrapper for Boolean. We need this wrapped Boolean because
	 * endpoints functions must return an object instance, they can't return a
	 * Type class such as String or Integer or Boolean
	 */
	public static class WrappedBoolean {

		private final Boolean result;
		private final String reason;

		public WrappedBoolean(Boolean result) {
			this.result = result;
			this.reason = "";
		}

		public WrappedBoolean(Boolean result, String reason) {
			this.result = result;
			this.reason = reason;
		}

		public Boolean getResult() {
			return result;
		}

		public String getReason() {
			return reason;
		}
	}

	/**
     * Register to attend the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return Boolean true when success, otherwise false
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "registerForConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.POST
    )

    public WrappedBoolean registerForConference(final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException,
            ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId
        final String userId = user.getUserId();

        // TODO
        // Start transaction
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>()
        {
        	public WrappedBoolean run()
        	{
                try {
                	Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
                	// TODO
                	// Get the Conference entity from the datastore
                	Conference conference = ofy().load().key(conferenceKey).now();
                	// 404 when there is no Conference with the given conferenceId.
                	if (conference == null) {
                		return new WrappedBoolean (false, "No Conference found with key: " + websafeConferenceKey);
                	}
                	// TODO
                	// Get the user's Profile entity
                	Profile profile = getProfileFromUser(user);
                	// Has the user already registered to attend this conference?
                	if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                		return new WrappedBoolean (false, "Already registered");
                	} else if (conference.getSeatsAvailable() <= 0) {
                		return new WrappedBoolean (false, "No seats available");
                	} else {
                		// All looks good, go ahead and book the seat
                		profile.addToConferenceKeysToAttend(websafeConferenceKey);
                		conference.bookSeats(1);
                		ofy().save().entities(profile, conference).now();
                        // We are booked!
                		return new WrappedBoolean(true, "Registration successful");
                   }

                } catch (Exception e) {
                	return new WrappedBoolean(false, "Unknown exception");
                }
        	}
        });
     // if result is false
    	if(!result.getResult())
    	{
    		if(result.getReason().contains("No Conference found with key"))
    		{
    			throw new NotFoundException(result.getReason());
    		}
    		else if(result.getReason()=="Already registered")
    		{
    			throw new ConflictException("You have already registered");
    		}
    		else if(result.getReason()=="No seats available")
    		{
    			throw new ConflictException("There are no seats available");
    		}
    		else
    		{
    			throw new ForbiddenException("Unknown exception");
    		}
		}
    }
	/**
	 * Returns a collection of Conference Object that the user is going to
	 * attend.
	 *
	 * @param user
	 *            An user who invokes this method, null when the user is not
	 *            signed in.
	 * @return a Collection of Conferences that the user is going to attend.
	 * @throws UnauthorizedException
	 *             when the User object is null.
	 */
	@ApiMethod(name = "getConferencesToAttend", path = "getConferencesToAttend", httpMethod = HttpMethod.GET)
	public Collection<Conference> getConferencesToAttend(final User user)
			throws UnauthorizedException, NotFoundException {
		// If not signed in, throw a 401 error.
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}
		// TODO
		// Get the Profile entity for the user
		Profile profile = null; // Change this;
		if (profile == null) {
			throw new NotFoundException("Profile doesn't exist.");
		}

		// TODO
		// Get the value of the profile's conferenceKeysToAttend property
		List<String> keyStringsToAttend = null; // change this

		// TODO
		// Iterate over keyStringsToAttend,
		// and return a Collection of the
		// Conference entities that the user has registered to atend

		return null; // change this
	}
}
