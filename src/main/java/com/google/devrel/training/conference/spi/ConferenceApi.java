package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.factory;
import static com.google.devrel.training.conference.service.OfyService.ofy;

import java.util.ArrayList;
import java.util.List;
import com.googlecode.objectify.cmd.Query;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = {
        Constants.WEB_CLIENT_ID, Constants.API_EXPLORER_CLIENT_ID }, description = "API for the Conference Central Backend application.")
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

        if (user == null) throw new UnauthorizedException("You have not logged in.");
        if (pf != null) teeShirtSize = pf.getTeeShirtSize() != null ? pf.getTeeShirtSize() : TeeShirtSize.NOT_SPECIFIED;
        if (pf != null && pf.getDisplayName() != null) displayName = pf.getDisplayName();

        userId = user.getUserId();
        mainEmail = user.getEmail();

        if (user.getEmail() != null && (displayName == null /*|| displayName.equals("Your Name Here")*/))
        	displayName = extractDefaultDisplayNameFromEmail(mainEmail);

        Profile profile = getProfile(user);
        if (profile == null)
        	profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
        else profile.update(displayName, teeShirtSize);

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
     * Gets the Profile entity for the current user
     * or creates it if it doesn't exist
     * @param user
     * @return user's Profile
     */
    private static Profile getProfileFromUser(User user) {
        // First fetch the user's Profile from the datastore.
        Profile profile = ofy().load().key(
                Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            // Create a new Profile if it doesn't exist.
            // Use default displayName and teeShirtSize
            String email = user.getEmail();
            profile = new Profile(user.getUserId(),
                    extractDefaultDisplayNameFromEmail(email), email, TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }
    
    /**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
        throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        String userId = user.getUserId();

        Key<Profile> profileKey =  Key.create(Profile.class, user.getUserId());
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);

        final long conferenceId = conferenceKey.getId();
        Profile profile = getProfileFromUser(user);
        profile.addToCreatedConferenceKeys(conferenceKey.toString());
        Conference conference = ofy().load().key(Key.create(profileKey, Conference.class, conferenceId)).now();
        if (conference == null)
        	conference = new Conference(conferenceId, userId, conferenceForm);
        else conference.updateWithConferenceForm(conferenceForm);
        ofy().save().entities(profile, conference).now();

        return conference;
    }
    
    @ApiMethod(
            name = "queryConferences",
            path = "queryConferences",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> queryConferences() {
        Query query = ofy().load().type(Conference.class).order("name");
        return query.list();
    }
    
    @ApiMethod(
            name = "getConferencesCreated",
            path = "getConferencesCreated",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesCreated(final User user) throws UnauthorizedException
    {
    	if (user == null) 
    	{
            throw new UnauthorizedException("Authorization required");
        }
    	Profile profile = getProfileFromUser(user);
    	List<Conference> created = new ArrayList<Conference>(0);
    	if (profile != null)
    	{
    		for (String key : profile.getCreatedConferenceKeys())
        	{
        		created.add(ofy().load().key(Key.create(Conference.class, key)).now());
        	}
    	}
    	return created;
    }
    
    @ApiMethod(
            name = "getConferencesFiltered",
            path = "getConferencesFiltered",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesFiltered()
    {
    	Query query = ofy().load().type(Conference.class);
        query = query.filter("city =", "London");
        query = query.filter("topics =", "Web Technologies");
        return query.list();
    }
}
