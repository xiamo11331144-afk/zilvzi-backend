package com.zilvzi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import com.zilvzi.service.UserService;
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
import java.util.UUID;

/**
 * 用户Service实现类
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String appSecret;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public User register(String username, String password, String phone, String email, String nickname, String province, String city, String district) {
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(username) != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查手机号是否已存在
        if (phone != null && userMapper.selectByPhone(phone) != null) {
            throw new RuntimeException("手机号已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(username != null && !username.isEmpty() ? username : null);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setEmail(email != null && !email.isEmpty() ? email : null);
        user.setNickname(nickname != null && !nickname.isEmpty() ? nickname : ("用户" + phone));
        user.setLevel(1);
        user.setExperience(0);
        user.setRole(0);
        user.setProvince(province);
        user.setCity(city);
        user.setDistrict(district);

        userMapper.insert(user);
        return user;
    }

    @Override
    public User loginByPhone(String phone, String code) {
        // 验证验证码
        String cacheCode = redisUtil.get("verify_code:" + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            throw new RuntimeException("验证码错误或已过期");
        }

        // 查找用户
        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 删除验证码缓存
        redisUtil.del("verify_code:" + phone);

        return user;
    }

    @Override
    public User loginByEmail(String email, String code) {
        // 验证验证码
        String cacheCode = redisUtil.get("verify_code:" + email);
        if (cacheCode == null || !cacheCode.equals(code)) {
            throw new RuntimeException("验证码错误或已过期");
        }

        // 查找用户
        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 删除验证码缓存
        redisUtil.del("verify_code:" + email);

        return user;
    }

    @Override
    public User loginByUsername(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码（BCrypt）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        return user;
    }

    @Override
    public User loginByWechat(String code, String province, String city, String district) {
        try {
            System.out.println("[WeChat] 登录参数: province=" + province + ", city=" + city + ", district=" + district);
            // 调用微信API获取openid
            String url = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    appId, appSecret, code
            );

            System.out.println("[WeChat] 请求微信API获取openid...");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("[WeChat] 响应: " + response.body());

            JsonNode jsonNode = objectMapper.readTree(response.body());

            // 检查是否有错误
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errMsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "未知错误";
                throw new RuntimeException("微信登录失败: " + errMsg);
            }

            String openid = jsonNode.get("openid").asText();
            System.out.println("[WeChat] 获取到openid: " + openid);

            // 根据openid查找用户
            User user = userMapper.selectByOpenid(openid);
            if (user == null) {
                // 自动注册
                System.out.println("[WeChat] 用户不存在，自动注册");
                user = new User();
                user.setOpenid(openid);
                user.setNickname("微信用户");
                user.setLevel(1);
                user.setExperience(0);
                user.setRole(0);
                user.setProvince(province);
                user.setCity(city);
                user.setDistrict(district);
                userMapper.insert(user);
            } else {
                System.out.println("[WeChat] 找到已有用户: " + user.getId());
                // 更新地域信息（如果前端传了）
                if (province != null && !province.isEmpty()) {
                    user.setProvince(province);
                    user.setCity(city);
                    user.setDistrict(district);
                    userMapper.update(user);
                }
            }

            return user;
        } catch (Exception e) {
            System.out.println("[WeChat] 登录异常: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("微信登录失败: " + e.getMessage());
        }
    }

    @Override
    public void sendVerifyCode(String target, String type) {
        // 生成6位验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        // 保存到Redis，5分钟有效
        redisUtil.setex("verify_code:" + target, code, 300);

        // TODO: 发送短信或邮件
        System.out.println("验证码发送到 " + target + ": " + code);
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public void updateUser(User user) {
        userMapper.update(user);
    }

    @Override
    public void updateAvatar(Long id, String avatar) {
        userMapper.updateAvatar(id, avatar);
    }
}