package com.zilvzi.controller;

import com.zilvzi.mapper.MessageMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.pojo.Message;
import com.zilvzi.pojo.Partner;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.NotificationService;
import com.zilvzi.utils.SensitiveWordFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {
    @Mock MessageMapper messageMapper;
    @Mock PartnerMapper partnerMapper;
    @Mock NotificationService notificationService;
    @Mock SensitiveWordFilter sensitiveWordFilter;
    @InjectMocks MessageController controller;

    @Test void sendingMessageIsRejectedWhenEitherSideHasBlockedTheOther() {
        Partner blocked = new Partner();
        blocked.setUserId(9L);
        blocked.setPartnerId(42L);
        blocked.setStatus("blocked");
        when(sensitiveWordFilter.containsSensitive("hello")).thenReturn(false);
        when(partnerMapper.selectAllByUserAndPartner(42L, 9L)).thenReturn(List.of(blocked));

        Result<?> result = controller.sendMessage(42L, Map.of("toUserId", "9", "content", "hello"));

        assertNotEquals(200, result.getCode());
        verify(messageMapper, never()).insert(any(Message.class));
        verifyNoInteractions(notificationService);
    }

    @Test void sendingMessageRemainsAllowedForUnblockedUsersWithoutExistingRelationship() {
        when(sensitiveWordFilter.containsSensitive("hello")).thenReturn(false);
        when(partnerMapper.selectAllByUserAndPartner(42L, 9L)).thenReturn(List.of());

        Result<?> result = controller.sendMessage(42L, Map.of("toUserId", "9", "content", "hello"));

        assertEquals(200, result.getCode());
        verify(messageMapper).insert(any(Message.class));
        verify(notificationService).sendNotification(9L, 42L, "private_message", "私信", "给你发来一条消息", null, null);
    }
    @Test void conversationReturnsNewestPageInAscendingOrderAndReadsOnlyReturnedInboundMessages() {
        Message newest = message(30L, 9L, 42L, 0);
        Message middle = message(20L, 42L, 9L, 0);
        when(messageMapper.selectConversation(42L, 9L, 0, 2)).thenReturn(List.of(newest, middle));

        Result<?> result = controller.getConversation(42L, 9L, 1, 2);

        assertEquals(200, result.getCode());
        List<Message> returned = (List<Message>) result.getData();
        assertEquals(List.of(middle.getId(), newest.getId()), returned.stream().map(Message::getId).toList());
        verify(messageMapper).markMessagesAsRead(42L, 9L, List.of(newest.getId()));
        verify(messageMapper, never()).markConversationAsRead(anyLong(), anyLong());
    }

    @Test void conversationPageAndSizeAreClamped() {
        when(messageMapper.selectConversation(42L, 9L, 0, 100)).thenReturn(List.of());
        Result<?> result = controller.getConversation(42L, 9L, 0, 1000);
        assertEquals(200, result.getCode());
        verify(messageMapper).selectConversation(42L, 9L, 0, 100);
        verify(messageMapper, never()).markMessagesAsRead(anyLong(), anyLong(), anyList());
    }

    @Test void conversationListSizeIsClampedAndResponseRemainsArray() {
        when(messageMapper.selectConversations(42L, 0, 100)).thenReturn(List.of(Map.of("partnerId", 9L)));
        Result<?> result = controller.getConversations(42L, 0, 1000);
        assertEquals(200, result.getCode());
        assertInstanceOf(List.class, result.getData());
        verify(messageMapper).selectConversations(42L, 0, 100);
    }

    private Message message(Long id, Long from, Long to, Integer read) {
        Message message = new Message();
        message.setId(id);
        message.setFromUserId(from);
        message.setToUserId(to);
        message.setIsRead(read);
        return message;
    }
}
