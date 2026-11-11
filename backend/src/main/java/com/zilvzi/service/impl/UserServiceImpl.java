package com.zilvzi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import com.zilvzi.service.UserService;
import com.zilvzi.service.MentorMatchingService;
import com.zilvzi.service.VerificationCodeSender;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.zilvzi.utils.JwtUtil;
import com.zilvzi.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MentorMatchingService mentorMatchingService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private VerificationCodeSender verificationCodeSender;
    @Value("${wechat.appid}")
    private String appId;
    @Value("${wechat.secret}")
    private String appSecret;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom codeRandom = new SecureRandom();

    @Override
    public User register(String username, String password, String phone, String email, String nickname, String province, String city, String district) {
        if (userMapper.selectByUsername(username) != null) throw new RuntimeException("用户名已存在");
        if (phone != null && userMapper.selectByPhone(phone) != null) throw new RuntimeException("手机号已被注册");
        User user = new User();
        user.setUsername(username != null && !username.isEmpty() ? username : null);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setEmail(email != null && !email.isEmpty() ? email : null);
        user.setNickname(nickname != null && !nickname.isEmpty() ? nickname : ("用户" + phone));
        user.setLevel(1);
        user.setExperience(0);
        user.setRole(0);
        user.setMentorEnabled(0);
        user.setProvince(province);
        user.setCity(city);
        user.setDistrict(district);
        userMapper.insert(user);
        return user;
    }

    @Override
    public User loginByPhone(String phone, String code) {
        String cacheCode = redisUtil.get("verify_code:" + phone);
        if (cacheCode == null || !cacheCode.equals(code)) throw new RuntimeException("验证码错误或已过期");
        User user = userMapper.selectByPhone(phone);
        if (user == null) throw new RuntimeException("用户不存在");
        redisUtil.del("verify_code:" + phone);
        return user;
    }

    @Override
    public User loginByEmail(String email, String code) {
        String cacheCode = redisUtil.get("verify_code:" + email);
        if (cacheCode == null || !cacheCode.equals(code)) throw new RuntimeException("验证码错误或已过期");
        User user = userMapper.selectByEmail(email);
        if (user == null) throw new RuntimeException("用户不存在");
        redisUtil.del("verify_code:" + email);
        return user;
    }

    @Override
    public User loginByUsername(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) throw new RuntimeException("用户不存在");
        if (!passwordEncoder.matches(password, user.getPassword())) throw new RuntimeException("密码错误");
        return user;
    }

    @Override
    public User loginByWechat(String code, String province, String city, String district) {
        try {
            String url = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    appId, appSecret, code);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode jsonNode = objectMapper.readTree(response.body());
            if (response.statusCode() != 200 || jsonNode == null
                    || jsonNode.path("errcode").asInt(0) != 0 || jsonNode.path("openid").asText("").isBlank()) {
                throw new IllegalStateException("Invalid WeChat response");
            }
            String openid = jsonNode.get("openid").asText();
            User user = userMapper.selectByOpenid(openid);
            if (user == null) {
                user = new User();
                user.setOpenid(openid);
                user.setNickname("微信用户");
                user.setLevel(1);
                user.setExperience(0);
                user.setRole(0);
                user.setMentorEnabled(0);
                user.setProvince(province);
                user.setCity(city);
                user.setDistrict(district);
                userMapper.insert(user);
            } else if (province != null && !province.isEmpty()) {
                user.setProvince(province);
                user.setCity(city);
                user.setDistrict(district);
                userMapper.update(user);
            }
            return user;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("微信登录中断，请稍后重试");
        } catch (Exception e) {
            // Session bodies, openids and exception URLs must never enter logs or client errors.
            throw new RuntimeException("微信登录失败，请稍后重试");
        }
    }

    @Override
    public void sendVerifyCode(String target, String type) {
        boolean validPhone = "phone".equals(type) && target != null && target.matches("^1[3-9]\\d{9}$");
        boolean validEmail = "email".equals(type) && target != null && target.length() <= 254
                && target.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        if (!validPhone && !validEmail) throw new IllegalArgumentException("请输入正确的手机号或邮箱");
        String code = String.valueOf(100000 + codeRandom.nextInt(900000));
        // Cache only after the provider acknowledges delivery; an unconfigured service must not fake success.
        verificationCodeSender.send(target, type, code);
        redisUtil.setex("verify_code:" + target, code, 300);
    }

    @Override
    public User getUserById(Long id) { return userMapper.selectById(id); }
    @Override
    public void updateUser(User user) { userMapper.update(user); }
    @Override
    public void updateAvatar(Long id, String avatar) { userMapper.updateAvatar(id, avatar); }
    @Override
    public java.util.Map<String, Object> getMentorStatus(Long userId) { return mentorMatchingService.getStatus(userId); }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void setMentorEnabled(Long userId, boolean enabled) {
        if (userMapper.selectByIdForUpdate(userId) == null) throw new IllegalArgumentException("用户不存在");
        if (enabled && !Boolean.TRUE.equals(getMentorStatus(userId).get("eligible"))) {
            throw new IllegalArgumentException("近30天有效打卡至少24天（80%），且当前连续打卡至少7天后才能开启引领匹配");
        }
        userMapper.updateMentorEnabled(userId, enabled ? 1 : 0);
    }
}
