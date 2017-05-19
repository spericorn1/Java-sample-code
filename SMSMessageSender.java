package com.sample.messaging;

import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sample.Configuration;
import com.sample.enums.MessageType;
import com.sample.model.Account;
import com.sample.model.Program;
import com.sample.model.ProgramPlay;
import com.sample.model.Session;
import com.sample.service.NotificationService;
import com.sample.service.SessionService;
import com.cs.stack.messaging.MessageSender;
import com.cs.stack.messaging.email.EmailMessage;
import com.cs.stack.messaging.sms.SmsMessage;

@Singleton
public class SMSMessageSender {

  @Inject
  private MessageSender<SmsMessage> smsMessageSender;

  @Inject
  private SessionService sessionService;

  @Inject
  private NotificationService notificationService;

  @Inject
  private Configuration configuration;

  private Logger LOG = getLogger(SMSMessageSender.class);

  public void sendDHIRiskNotification(ProgramPlay programPlay, Account account) {
    Session session = sessionService.findSessionByProgramPlayId(programPlay.getProgramPlayId());
    if (StringUtils.isNotBlank(session.getPatientIdentifier())) {
      if (account.getMobilePhone() != null && account.getMobilePhone().trim().length() == 10) {
        String msg = format("MyApp ALERT : %s/%s/%s", configuration.getDeploymentUrl(), "session", programPlay.getSessionId());
        SmsMessage message = SmsMessage.Builder.withBody(msg).to(account.getMobilePhone()).build();
        notificationService.insertNotificationRecord(account.getAccountId(), programPlay.getProgramPlayId(), msg,
            account.getMobilePhone().trim(), MessageType.SMS);
        smsMessageSender.sendMessage(message);
      } else {
        LOG.error("No mobile phone setup for " + account.getName());
      }
    } else {
      LOG.error("Empty Patient ID for At Risk Patient " + programPlay.getProgramPlayId());
    }
  }
  
  public void sendDHINotification(ProgramPlay programPlay, Account account) {
    Session session = sessionService.findSessionByProgramPlayId(programPlay.getProgramPlayId());
    if (StringUtils.isNotBlank(session.getPatientIdentifier())) {
      if (account.getMobilePhone() != null && account.getMobilePhone().trim().length() == 10) {
        String msg = format("MyApp : %s/%s/%s", configuration.getDeploymentUrl(), "session",
            programPlay.getSessionId());
        SmsMessage message = SmsMessage.Builder.withBody(msg).to(account.getMobilePhone()).build();
        notificationService.insertNotificationRecord(account.getAccountId(), programPlay.getProgramPlayId(), msg,
            account.getMobilePhone().trim(), MessageType.SMS);
        smsMessageSender.sendMessage(message);
      } else {
        LOG.error("No mobile phone setup for " + account.getName());
      }
    } else {
      LOG.error("Empty Patient ID for At Risk Patient " + programPlay.getProgramPlayId());
    }
  }
  
  public void sendShareProgramMessage(String phoneNumber, String key, Program program, String languageTag) {
    String msg = format("Click to see your Personalized Careplan: http://sample.com/static/12345-ICWATQBVAE.pdf");
    SmsMessage message = SmsMessage.Builder.withBody(msg).to(phoneNumber).build();
    smsMessageSender.sendMessage(message);
  }
}