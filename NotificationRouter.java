package com.sample.messaging;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sample.enums.MessageType;
import com.sample.enums.NotificationType;
import com.sample.model.Account;
import com.sample.model.NotificationSubscription;
import com.sample.model.ProgramPlay;
import com.sample.service.AccountService;
import com.sample.service.NotificationService;
import com.sample.service.ProgramPlayService;
import com.sample.service.ProgramService;

@Singleton
public class NotificationRouter {
  @Inject
  private SMSMessageSender SMSMessageSender;

  @Inject
  private EmailMessageSender EmailMessageSender;

  @Inject
  private AccountService accountService;

  @Inject
  private ProgramPlayService programPlayService;

  @Inject
  private NotificationService notificationService;

  @Inject
  private ProgramService programService;

  public void sendDHIRiskNotification(Long programPlayId, Long companyId) { 

    ProgramPlay programPlay = programPlayService.findProgramPlayById(programPlayId);

    List<NotificationSubscription> notificationSubscriptions = notificationService
        .findNotificationSubscriptions(companyId, programPlay.getProgramId(), NotificationType.RISK_NOTIFICATION);

    for (NotificationSubscription notificationSubscription : notificationSubscriptions) {
      if (!notificationService.wasNotificationSentForProgramPlay(notificationSubscription.getAccountId(),
          programPlayId)) {
        Account account = accountService.findAccountByAccountId(notificationSubscription.getAccountId());
        if (notificationSubscription.getMessageType().equals(MessageType.SMS))
          // get preferred message type,
          SMSMessageSender.sendDHIRiskNotification(programPlay, account);
        else
          EmailMessageSender.sendRiskNotificationEmail(programPlay, account);
      }
    }
  }

  // These are one off notification based on questions and answers
  public void sendDHINotification(Long companyId, Long programPlayId, Long questionId, List<Long> videoQuestionAnswerIds) { // Account signedInAccount, MessageType messageType
    // and if should be sent to admin or social workers

    ProgramPlay programPlay = programPlayService.findProgramPlayById(programPlayId);
    
    List<NotificationSubscription> notificationSubscriptions = notificationService.findNotificationSubscriptions(
        companyId, programPlay.getProgramId(), NotificationType.QUESTION_ANSWER_NOTIFICATION);

    for (NotificationSubscription notificationSubscription : notificationSubscriptions) {    
      if (!notificationService.wasNotificationSentForProgramPlay(notificationSubscription.getAccountId(), programPlayId)) {
        Account account = accountService.findAccountByAccountId(notificationSubscription.getAccountId());
        if (notificationSubscription.getMessageType().equals(MessageType.EMAIL)){        
          EmailMessageSender.sendNotificationEmail(programPlay, account);
        }else{
          SMSMessageSender.sendDHINotification(programPlay, account);
        }
      }
    }
  }
}