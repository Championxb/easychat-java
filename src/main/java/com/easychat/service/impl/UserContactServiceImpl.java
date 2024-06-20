package com.easychat.service.impl;

import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.SysSettingDto;
import com.easychat.entity.dto.UserContactSearchResultDto;
import com.easychat.entity.enums.*;
import com.easychat.entity.po.*;
import com.easychat.entity.query.*;
import com.easychat.entity.vo.PaginationResultVO;
import com.easychat.exception.BusinessException;
import com.easychat.mappers.*;
import com.easychat.redis.RedisComponet;
import com.easychat.service.UserContactService;
import com.easychat.utils.CopyTools;
import com.easychat.utils.StringTools;
import com.easychat.websocket.ChannelContextUtils;
import com.easychat.websocket.MessageHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * 联系人 业务接口实现
 */
@Service("userContactService")
public class UserContactServiceImpl implements UserContactService {

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private RedisComponet redisComponet;

    @Resource
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private ChannelContextUtils channelContextUtils;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserContact> findListByParam(UserContactQuery param) {
        return this.userContactMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserContactQuery param) {
        return this.userContactMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserContact> findListByPage(UserContactQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserContact> list = this.findListByParam(param);
        PaginationResultVO<UserContact> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserContact bean) {
        return this.userContactMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserContact> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userContactMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserContact> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userContactMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserContact bean, UserContactQuery param) {
        StringTools.checkParam(param);
        return this.userContactMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserContactQuery param) {
        StringTools.checkParam(param);
        return this.userContactMapper.deleteByParam(param);
    }

    /**
     * 根据UserIdAndContactId获取对象
     */
    @Override
    public UserContact getUserContactByUserIdAndContactId(String userId, String contactId) {
        return this.userContactMapper.selectByUserIdAndContactId(userId, contactId);
    }

    /**
     * 根据UserIdAndContactId修改
     */
    @Override
    public Integer updateUserContactByUserIdAndContactId(UserContact bean, String userId, String contactId) {
        return this.userContactMapper.updateByUserIdAndContactId(bean, userId, contactId);
    }

    /**
     * 根据UserIdAndContactId删除
     */
    @Override
    public Integer deleteUserContactByUserIdAndContactId(String userId, String contactId) {
        return this.userContactMapper.deleteByUserIdAndContactId(userId, contactId);
    }

    @Override
    public UserContactSearchResultDto searchContact(String userId, String contactId) {
        UserContactTypeEnum typeEnum = UserContactTypeEnum.getByPrefix(contactId);
        if (typeEnum == null) {
            return null;
        }
        UserContactSearchResultDto resultDto = new UserContactSearchResultDto();
        switch (typeEnum) {
            case USER:
                UserInfo userInfo = userInfoMapper.selectByUserId(contactId);
                if (userInfo == null) {
                    return null;
                }
                resultDto = CopyTools.copy(userInfo, UserContactSearchResultDto.class);
                break;
            case GROUP:
                GroupInfo groupInfo = groupInfoMapper.selectByGroupId(contactId);
                if (null == groupInfo) {
                    return null;
                }
                resultDto.setNickName(groupInfo.getGroupName());
                break;
        }
        resultDto.setContactType(typeEnum.toString());
        resultDto.setContactId(contactId);

        if (userId.equals(contactId)) {
            resultDto.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            return resultDto;
        }
        //查询是否已经是好友
        UserContact userContact = this.userContactMapper.selectByUserIdAndContactId(userId, contactId);
        resultDto.setStatus(userContact == null ? null : userContact.getStatus());
        return resultDto;
    }

    @Override
    public void addContact(String applyUserId, String receiveUserId, String contactId, Integer contactType, String applyInfo) {
        //群人上限判断
        if (UserContactTypeEnum.GROUP.getType().equals(contactType)) {
            UserContactQuery contactQuery = new UserContactQuery();
            contactQuery.setContactId(contactId);
            contactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            Integer count = userContactMapper.selectCount(contactQuery);
            SysSettingDto sysSettingDto = redisComponet.getSysSetting();
            if (count >= sysSettingDto.getMaxGroupMemberCount()) {
                throw new BusinessException("成员已满，无法加入");
            }
        }
        Date curDate = new Date();
        //同意 双方添加为好友
        List<UserContact> contactList = new ArrayList<>();
        //申请人添加对方
        UserContact userContact = new UserContact();
        userContact.setUserId(applyUserId);
        userContact.setContactId(contactId);
        userContact.setContactType(contactType);
        userContact.setCreateTime(curDate);
        userContact.setLastUpdateTime(curDate);
        userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        contactList.add(userContact);
        //如果是申请好友 接收人添加申请人  群组不用添加对方为好友
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            userContact = new UserContact();
            userContact.setUserId(receiveUserId);
            userContact.setContactId(applyUserId);
            userContact.setContactType(contactType);
            userContact.setCreateTime(curDate);
            userContact.setLastUpdateTime(curDate);
            userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            contactList.add(userContact);
        }
        //批量加入
        userContactMapper.insertOrUpdateBatch(contactList);

        //如果是好友申请,接收人也添加申请人为联系人
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            redisComponet.addUserContact(receiveUserId, applyUserId);
        }
        //审核通过，将申请人的联系人添加上 我 或 群组
        redisComponet.addUserContact(applyUserId, contactId);


        //创建会话信息
        String sessionId = null;
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            sessionId = StringTools.getChatSessionId4User(new String[]{applyUserId, contactId});
        } else {
            sessionId = StringTools.getChatSessionId4Group(contactId);
        }

        //会话参与人
        List<ChatSessionUser> chatSessionUserList = new ArrayList<>();
        if (UserContactTypeEnum.USER.getType().equals(contactType)) {
            //创建会话
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(sessionId);
            chatSession.setLastReceiveTime(curDate.getTime());
            chatSession.setLastMessage(applyInfo);
            this.chatSessionMapper.insertOrUpdate(chatSession);

            //申请人session
            ChatSessionUser applySessionUser = new ChatSessionUser();
            applySessionUser.setUserId(applyUserId);
            applySessionUser.setContactId(contactId);
            applySessionUser.setSessionId(sessionId);
            applySessionUser.setLastReceiveTime(curDate.getTime());
            applySessionUser.setLastMessage(applyInfo);
            //查询接收人信息
            UserInfo contactUser = this.userInfoMapper.selectByUserId(contactId);
            applySessionUser.setContactName(contactUser.getNickName());
            chatSessionUserList.add(applySessionUser);

            //接受人session
            ChatSessionUser contactSessionUser = new ChatSessionUser();
            contactSessionUser.setUserId(contactId);
            contactSessionUser.setContactId(applyUserId);
            contactSessionUser.setSessionId(sessionId);
            contactSessionUser.setLastReceiveTime(curDate.getTime());
            contactSessionUser.setLastMessage(applyInfo);
            //查询申请人信息
            UserInfo applyUserInfo = this.userInfoMapper.selectByUserId(applyUserId);
            contactSessionUser.setContactName(applyUserInfo.getNickName());
            chatSessionUserList.add(contactSessionUser);
            this.chatSessionUserMapper.insertOrUpdateBatch(chatSessionUserList);

            //记录消息消息表
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSessionId(sessionId);
            chatMessage.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            chatMessage.setMessageContent(applyInfo);
            chatMessage.setSendUserId(applyUserId);
            chatMessage.setSendUserNickName(applyUserInfo.getNickName());
            chatMessage.setSendTime(curDate.getTime());
            chatMessage.setContactId(contactId);
            chatMessage.setContactType(UserContactTypeEnum.USER.getType());
            chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
            chatMessageMapper.insert(chatMessage);

            MessageSendDto messageSendDto = CopyTools.copy(chatMessage, MessageSendDto.class);
            /**
             * 发送给接受好友申请的人
             */
            messageHandler.sendMessage(messageSendDto);

            /**
             * 发送给申请人 发送人就是接收人，联系人就是申请人
             */
            messageSendDto.setMessageType(MessageTypeEnum.ADD_FRIEND_SELF.getType());
            messageSendDto.setContactId(applyUserId);
            messageSendDto.setExtendData(contactUser);
            messageHandler.sendMessage(messageSendDto);

        } else {
            //加入群组
            ChatSessionUser chatSessionUser = new ChatSessionUser();
            chatSessionUser.setUserId(applyUserId);
            chatSessionUser.setContactId(contactId);
            GroupInfo groupInfo = this.groupInfoMapper.selectByGroupId(contactId);
            chatSessionUser.setContactName(groupInfo.getGroupName());
            chatSessionUser.setSessionId(sessionId);
            this.chatSessionUserMapper.insertOrUpdate(chatSessionUser);

            //将群组加入到用户的联系人列表
            redisComponet.addUserContact(applyUserId, groupInfo.getGroupId());

            channelContextUtils.addUser2Group(applyUserId, groupInfo.getGroupId());


            UserInfo applyUserInfo = this.userInfoMapper.selectByUserId(applyUserId);

            String sendMessage = String.format(MessageTypeEnum.ADD_GROUP.getInitMessage(), applyUserInfo.getNickName());

            //增加session信息
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(sessionId);
            chatSession.setLastReceiveTime(curDate.getTime());
            chatSession.setLastMessage(sendMessage);
            this.chatSessionMapper.insertOrUpdate(chatSession);

            //增加聊天消息
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSessionId(sessionId);
            chatMessage.setMessageType(MessageTypeEnum.ADD_GROUP.getType());
            chatMessage.setMessageContent(sendMessage);
            chatMessage.setSendUserId(null);
            chatMessage.setSendUserNickName(null);
            chatMessage.setSendTime(curDate.getTime());
            chatMessage.setContactId(contactId);
            chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
            chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
            chatMessageMapper.insert(chatMessage);

            //发送群消息
            MessageSendDto messageSend = CopyTools.copy(chatMessage, MessageSendDto.class);
            messageSend.setContactId(groupInfo.getGroupId());
            //获取群人数量
            UserContactQuery userContactQuery = new UserContactQuery();
            userContactQuery.setContactId(contactId);
            userContactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            Integer memberCount = this.userContactMapper.selectCount(userContactQuery);
            messageSend.setMemberCount(memberCount);
            messageSend.setContactName(groupInfo.getGroupName());
            messageHandler.sendMessage(messageSend);
        }
    }

    @Override
    public void removeUserContact(String userId, String contactId, UserContactStatusEnum statusEnum) {
        //移除好友
        UserContact userContact = new UserContact();
        userContact.setStatus(statusEnum.getStatus());
        userContactMapper.updateByUserIdAndContactId(userContact, userId, contactId);

        //好友中也移除自己
        UserContact friendContact = new UserContact();
        if (UserContactStatusEnum.DEL == statusEnum) {
            friendContact.setStatus(UserContactStatusEnum.DEL_BE.getStatus());
        } else if (UserContactStatusEnum.BLACKLIST == statusEnum) {
            friendContact.setStatus(UserContactStatusEnum.BLACKLIST_BE.getStatus());
        }
        userContactMapper.updateByUserIdAndContactId(friendContact, contactId, userId);
        //将我从对方的好友缓存中删除
        redisComponet.removeUserContact(contactId, userId);
        //将对方从我的列表中删除
        redisComponet.removeUserContact(userId, contactId);
    }

    @Override
    public void removeGroupContact(String userId, String groupId, String contactId, UserContactStatusEnum statusEnum) {
        GroupInfo groupInfo = groupInfoMapper.selectByGroupId(groupId);
        if (null == groupInfo || !groupInfo.getGroupOwnerId().equals(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContact userContact = new UserContact();
        userContact.setStatus(statusEnum.getStatus());
        userContactMapper.updateByUserIdAndContactId(userContact, contactId, groupId);
        //将群组从群员列表中删除
        redisComponet.removeUserContact(contactId, groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addContact4Robot(String userId) {
        Date curDate = new Date();

        SysSettingDto sysSettingDto = redisComponet.getSysSetting();
        String contactId = sysSettingDto.getRobotUid();
        String contactName = sysSettingDto.getRobotNickName();
        String senMessage = sysSettingDto.getRobotWelcome();
        senMessage = StringTools.cleanHtmlTag(senMessage);
        //增加机器人好友
        UserContact userContact = new UserContact();
        userContact.setUserId(userId);
        userContact.setContactId(contactId);
        userContact.setContactType(UserContactTypeEnum.USER.getType());
        userContact.setCreateTime(curDate);
        userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        userContact.setLastUpdateTime(curDate);
        userContactMapper.insert(userContact);

        //增加会话信息
        String sessionId = StringTools.getChatSessionId4User(new String[]{userId, contactId});
        ChatSession chatSession = new ChatSession();
        chatSession.setLastMessage(senMessage);
        chatSession.setSessionId(sessionId);
        chatSession.setLastReceiveTime(curDate.getTime());
        this.chatSessionMapper.insert(chatSession);

        ChatSessionUser applySessionUser = new ChatSessionUser();
        applySessionUser.setUserId(userId);
        applySessionUser.setContactId(contactId);
        applySessionUser.setContactName(contactName);
        applySessionUser.setSessionId(sessionId);
        this.chatSessionUserMapper.insertOrUpdate(applySessionUser);

        //增加聊天消息
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setMessageType(MessageTypeEnum.CHAT.getType());
        chatMessage.setMessageContent(senMessage);
        chatMessage.setSendUserId(contactId);
        chatMessage.setSendUserNickName(contactName);
        chatMessage.setSendTime(curDate.getTime());
        chatMessage.setContactId(userId);
        chatMessage.setContactType(UserContactTypeEnum.USER.getType());
        chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
        chatMessageMapper.insert(chatMessage);
    }
}