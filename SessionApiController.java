package com.sample.web.controller.api;

import static com.cs.stack.util.ValidationUtils.isValidEmailAddress;
import static com.cs.stack.util.ValidationUtils.isValidPhoneNumber;
import static com.cs.stack.web.ContentTypes.CONTENT_TYPE_JSON;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sample.Configuration;
import com.sample.WellTrackOneUtils;
import com.sample.command.PlayUpdateCommand;
import com.sample.command.SessionStartCommand;
import com.sample.command.SessionVitalsCommand;
import com.sample.enums.SuggestionTarget;
import com.sample.model.Account;
import com.sample.model.EnrollmentOption;
import com.sample.model.ProgramPlay;
import com.sample.model.QuestionAnswerSummary;
import com.sample.model.Risk;
import com.sample.model.Session;
import com.sample.model.SessionQuestion;
import com.sample.model.Suggestion;
import com.sample.service.EnrollmentService;
import com.sample.service.ProgramPlayService;
import com.sample.service.QuestionService;
import com.sample.service.ResultService;
import com.sample.service.SessionService;
import com.sample.task.GenerateCarePlan;
import com.sample.web.context.CurrentContext;
import com.cs.stack.exception.ValidationException;
import com.cs.stack.exception.ValidationException.FieldError;
import com.cs.stack.util.BackgroundTaskExecutor;
import java.util.logging.Level;

/**
 * @author Rajesh
 */
@Singleton
@Path("/api/session")
@Produces(CONTENT_TYPE_JSON)
public class SessionApiController {
  @Inject
  private Configuration configuration;

  @Inject
  private SessionService sessionService;

  @Inject
  private ResultService suggestionService;

  @Inject
  private QuestionService questionService;

  @Inject
  private ProgramPlayService programPlayService;

  @Inject
  private EnrollmentService enrollmentService;

  @Inject
  private CurrentContext currentContext;

  @Inject
  private GenerateCarePlan carePlan;
  
  @Inject
  private BackgroundTaskExecutor backgroundTaskExecutor;
  
  private Logger logger = getLogger(getClass());

  @POST
  @Path("/secure")
  public Map<String, Object> saveSecureSession(SessionStartCommand command) {
    byte[] valueDecoded= Base64.decodeBase64(command.getPatientIdentifier());
    String patientIdentifier = new String(valueDecoded);

    command.setPatientIdentifier(patientIdentifier);
    
    final String authToken = "2635a8f7d55793256c46442ab7e909096accac3f40e6b532c5175b7cba32c0ae";
    
    logger.debug("Saving session: " + patientIdentifier);
    
    final Long sessionId = sessionService.startSession(currentContext.getSignedInAccountId(), command);
    final List<SessionQuestion> sessionQuestions = questionService.findQuestionsForSession(currentContext.getSignedInAccount().getCompanyId());
    return new HashMap<String, Object>() {
      {
        put("sessionId", sessionId);
        put("sessionQuestions", sessionQuestions);
        put("authToken", authToken);
      }
    };
  }
  
  @POST
  public Map<String, Object> saveSession(SessionStartCommand command) {
    logger.debug("Saving session");
    final Long sessionId = sessionService.startSession(currentContext.getSignedInAccountId(), command);
    final List<SessionQuestion> sessionQuestions = questionService.findQuestionsForSession(currentContext.getSignedInAccount().getCompanyId());
    
    final Double age = sessionService.getPatientAgeSession(sessionId);
    return new HashMap<String, Object>() {
      {
        put("sessionId", sessionId);
        put("patientAge", age);
        put("sessionQuestions", sessionQuestions);        
      }
    };
  }

  @POST
  @Path("/sendemail")
  public Map<String, Object> sendemail(@QueryParam("email") String email, @QueryParam("sessionId") Long session_id, SessionVitalsCommand command) {
	ValidationException validationException = new ValidationException();
	final String emailAddress = trimToNull(email);
	    
	if (isBlank(emailAddress)) {
	  validationException.add(new FieldError("emailAddress", "An email address is required"));
	}
	else if (!isValidEmailAddress(emailAddress) && !isValidPhoneNumber(emailAddress)) {
	  validationException.add(new FieldError("emailAddress", "Not a valid email address"));
	}
	if (validationException.hasErrors()) {
	  throw validationException;
    }
    String filename = sessionService.getCareplan(session_id);
    sessionService.sendEmail(email, filename, command.getLanguageIdentifier());
    return new HashMap<String, Object>() {
      {
        put("success", true);
      }
    };
  }  
  
  @POST
  @Path("/vitals/getBlankVitalsForDate/{Date}/{Org_Id}")
  public Map<String, Object> getBlankVitalsForDate(@PathParam("Date") final String st_Date,@PathParam("Org_Id") final String st_Org_Id) { 
    // added st_Org_Id on 16-May-2017 by Rajesh to restrict records to Org Level
    String st_query="";
    
    if(!st_Date.equalsIgnoreCase("All"))
     st_query="select session.patient_name, patient_vital.patient_identifier, date(patient_vital.created_Date), session.session_id from "
          + "session,patient_vital,account where session.session_id=patient_vital.session_id and (patient_vital.height=0 or "
          + "patient_vital.weight=0 or patient_vital.systolic=0 or patient_vital.diastolic=0) and patient_vital.created_date >= '"+ st_Date +"'::date "
             + "AND patient_vital.created_date < ('"+ st_Date +"'::date + '1 day'::interval) and patient_vital.patient_identifier=session.patient_identifier "
             + "and account.account_id=session.account_id and account.company_id=" + st_Org_Id + " order by patient_vital.session_id ";

    else
      st_query="select session.patient_name, patient_vital.patient_identifier, date(patient_vital.created_Date), session.session_id from "
          + "session,patient_vital,account where session.session_id=patient_vital.session_id and (patient_vital.height=0 or "
          + " patient_vital.weight=0 or patient_vital.systolic=0 or patient_vital.diastolic=0) and patient_vital.patient_identifier=session.patient_identifier "
             + " and account.account_id=session.account_id and account.company_id=" + st_Org_Id +" order by patient_vital.session_id "; 

    try 
    {
        WellTrackOneUtils.init();        
        WellTrackOneUtils.establishPostgresConnection();
       
        final ArrayList hm=WellTrackOneUtils.genericHashMap(st_query,WellTrackOneUtils.getPostGresConnection()); 
        
        WellTrackOneUtils.closePostgresConnection();
            
        return new HashMap<String, Object>() {
          {                      
            if(hm!=null) 
                put("Vitals",hm); 
            else
                put("Vitals","");                                 
          }
        };
    }
    catch(Exception ex) {
        ex.printStackTrace();        
        java.util.logging.Logger.getLogger(SessionApiController.class.getName()).log(Level.SEVERE, null, ex);
    }      
    finally {
        WellTrackOneUtils.closePostgresConnection();
    }
    return null;     
  }
  
  @POST
  @Path("/vitals/{sessionId}/{selected_Patient_ID}/{provider_id}")
  public Map<String, Object> saveSessionWithVitals(@PathParam("sessionId") final Long sessionId,@PathParam("selected_Patient_ID") String st_Patient_ID,@PathParam("provider_id") String st_provider_id, SessionVitalsCommand command) throws Exception { 
               
    logger.debug("Saving Vitals");
    Session session = sessionService.findSessionById(sessionId);

    if (command.getPatientEmail() != null)
      sessionService.updatePatientEmail(sessionId, command.getPatientEmail());
    
    final Float bmi = sessionService.saveVitalInformation(currentContext.getSignedInAccountId(), sessionId, session.getPatientIdentifier(), command,st_provider_id,st_Patient_ID);
    
    System.out.println("Patient_ID: " + st_Patient_ID);
    carePlan.setSessionId(sessionId);
    UUID key = UUID.randomUUID();
    String filename = String.format("%s%s", key.toString(), ".pdf");
    carePlan.setFilename(filename);
    carePlan.setLanguage(command.getLanguageIdentifier());
    carePlan.setPassword(command.getPassword());
    carePlan.setPatient_ID(st_Patient_ID); //Added by Rajesh on 3-Apr to fetch Provider Name instead of UserName when showing PhysicianName
    System.out.println("Patient_ID from CarePlan Object: " + carePlan.getPatient_ID());
    backgroundTaskExecutor.execute(carePlan);
            
    final String carePlanUrl = String.format("%s%s%s", configuration.getCarePlanUrlBase(),"/", filename);
    
    sessionService.saveCarePlanForSession(sessionId, filename); //Added by Rajesh on 15-Apr to update the filename when "AddVitals" is clicked
    
    return new HashMap<String, Object>() {
      {
        put("sessionId", sessionId);
        put("carePlanUrl", carePlanUrl);
        put("bmi", bmi);
      }
    };
  }

  @POST
  @Path("/{sessionId}/enroll/{enrollmentId}")
  public Map<String, Object> enrollPatient(@PathParam("sessionId") Long sessionId,
      @PathParam("enrollmentId") Long enrollmentId) {
    Session session = sessionService.findSessionById(sessionId);
    enrollmentService.enrollmentPatient(enrollmentId, sessionId, session.getPatientIdentifier());
    return new HashMap<String, Object>() {
      {
        put("success", true);
      }
    };
  }

  @PUT
  @Path("/careplan/{sessionId}")
  public Map<String, Object> markReviewed(@PathParam("sessionId") Long sessionId) {
    Session session = sessionService.findSessionById(sessionId);

    return new HashMap<String, Object>() {
      {
        put("success", true);
      }
    };
  }

  @GET
  @Path("/patient/results/{sessionId}")
  public Map<String, Object> patientResults(@PathParam("sessionId") Long sessionId) {
    Account account = currentContext.getSignedInAccount();

    final List<Suggestion> suggestions = suggestionService.loadSuggestions(sessionId, SuggestionTarget.PATIENT);

    Session session = sessionService.findSessionById(sessionId);
    logger.debug(format("session id=> %d  atient id => %s", session.getSessionId(), session.getPatientIdentifier()));

    List<ProgramPlay> programPlays = programPlayService.findProgramPlaysBySessionId(sessionId);
    final List<EnrollmentOption> enrollments = new ArrayList<EnrollmentOption>();
    
    final Double age = sessionService.getPatientAgeSession(sessionId);
    
    if (StringUtils.isNotEmpty(session.getPatientIdentifier())) {
      for (ProgramPlay play : programPlays) {
        enrollments.addAll(enrollmentService.findEnrollments(account.getUnitId(), play.getProgramId(),
            session.getPatientIdentifier()));
      }
    }

    return new HashMap<String, Object>() {
      {
        put("suggestions", suggestions);
        put("enrollments", enrollments);
        put("patientAge", age);
      }
    };
  }
  
  @POST
  @Path("/provider/intermediate/results/{sessionId}/{selected_Patient_ID}/{selected_Provider_ID}")
  public Map<String, Object> providerIntermediateResults(@PathParam("sessionId") Long sessionId,@PathParam("selected_Patient_ID") String st_Patient_ID,@PathParam("selected_Provider_ID") String st_Provider_ID) { //Author Rajesh K.V.P; Date: 29-Mar

    final List<ProgramPlay> programPlays = programPlayService.findProgramPlaysBySessionId(sessionId);  
    final List<Map<String, Object>> sessionDetails = new ArrayList<Map<String, Object>>();
    
    ProgramPlay programPlay = programPlays.get(0);
    try {      
      String st_checkIfSessionEntryExists=WellTrackOneUtils.getTableColumnValue("select Session_ID from Wellness_eCastEMR_Data.dbo.MyAppVitals where Session_ID=" + sessionId);
      if(st_checkIfSessionEntryExists==null) {
        WellTrackOneUtils.fetchForMyAppVitalsInsert(sessionId,st_Patient_ID,st_Provider_ID);//calling here as per Chile's request on 29-Mar-2017; Author @Rajesh K.V.P; Provider_ID was added on 10-Apr-2017
      }
      WellTrackOneUtils.fetchForMyAppResultsInsert(programPlay.getProgramPlayId(),st_Patient_ID,st_Provider_ID); //calling here as per Chile's request on 29-Mar-2017; Author @Rajesh K.V.P; Provider_ID was added on 10-Apr-2017
    }
    catch(Exception ex) { 
      ex.printStackTrace();
      //do nothing as it is just an intermediate call and control has to proceed irrespective of success/failure
    }
    
    return new HashMap<String, Object>() {
      {
        put("success",true);
        put("operation", "success");        
      }
    };  
  }

  @GET
  @Path("/provider/results/{sessionId}")
  public Map<String, Object> providerResults(@PathParam("sessionId") Long sessionId) {

    final List<ProgramPlay> programPlays = programPlayService.findProgramPlaysBySessionId(sessionId);
    final List<Map<String, Object>> sessionDetails = new ArrayList<Map<String, Object>>();
    for (final ProgramPlay programPlay : programPlays) {
      final List<QuestionAnswerSummary> summaries = sessionService.findQuestionAnswerSummariesForVideoPlayId(programPlay.getProgramPlayId());

      sessionDetails.add(new HashMap<String, Object>() {
        {
          put("name", programPlay.getName());
          put("programPlayId", programPlay.getProgramPlayId());
          put("questionSummary", summaries);
        }
      });
    }

    final List<Risk> risks = sessionService.calculateAndLoadRiskForSession(sessionId);

    final List<Suggestion> suggestions = suggestionService.loadSuggestions(sessionId, SuggestionTarget.DOCTOR);
    
    return new HashMap<String, Object>() {
      {
        put("suggestions", suggestions);
        put("risks", risks);
        put("sessionDetails", sessionDetails);
      }
    };
  }
}
