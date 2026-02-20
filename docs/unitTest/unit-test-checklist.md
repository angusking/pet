# 单元测试清单（Mock 方案）

## 一、测试范围
- 类型：后端单元测试（基于 Mock）
- 目标层：`service`
- 技术栈：JUnit 5 + Mockito

## 二、通用规则
- 不启动 Spring 容器。
- 不连接数据库 / Redis / 网络。
- 外部依赖全部 Mock（`Repository`、`JwtService`、`PasswordEncoder`、`ObjectMapper`、`MultipartFile` 等）。
- 断言优先校验业务错误码与状态，不仅仅校验 message。

## 三、AuthService
文件：`backend/src/main/java/com/pet/service/AuthService.java`

1. `register_用户名已存在_应抛异常`
- 输入：已存在用户名
- Mock：
  - `userRepository.existsByUsername -> true`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.USERNAME_EXISTS`
  - `status == 409`
  - `userRepository.save` 不会被调用

2. `register_有效请求_应创建用户并返回token`
- 输入：合法注册请求
- Mock：
  - `existsByUsername -> false`
  - `passwordEncoder.encode -> hashed`
  - `userRepository.save -> user(id=1, role=USER)`
  - `jwtService.generateToken -> token`
- 预期：
  - 返回 `token`
  - 保存用户字段正确：`username/passwordHash/nickname/role/status`
  - `generateToken(1, "USER")` 调用 1 次

3. `login_用户不存在_应抛异常`
- 输入：不存在用户名
- Mock：
  - `findByUsername -> empty`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.INVALID_CREDENTIALS`
  - `status == 401`

4. `login_密码不匹配_应抛异常`
- 输入：存在用户 + 错误密码
- Mock：
  - `findByUsername -> user`
  - `passwordEncoder.matches -> false`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.INVALID_CREDENTIALS`

5. `login_成功_应返回token`
- 输入：正确用户名/密码
- Mock：
  - `findByUsername -> user(id=2, role=USER)`
  - `passwordEncoder.matches -> true`
  - `jwtService.generateToken -> token`
- 预期：
  - 返回 `token`
  - `generateToken(2, "USER")` 调用 1 次

## 四、PetService
文件：`backend/src/main/java/com/pet/service/PetService.java`

1. `getMyPrimaryPet_无数据_返回null`
- Mock：
  - `findByUserIdOrderByIsPrimaryDescIdAsc -> []`
- 预期：
  - 返回 `null`

2. `getMyPrimaryPet_有数据_返回主宠`
- Mock：
  - repository 返回按主宠优先排序的数据
- 预期：
  - 返回首条并正确映射

3. `listMyPets_应正确映射列表`
- Mock：
  - repository 返回 2 条宠物
- 预期：
  - 返回数量为 2
  - 字段映射正确：`id/name/breed/gender/birthday/weightKg/avatarUrl/primary/tags`

4. `getMyPetById_不存在_应抛异常`
- Mock：
  - `findByIdAndUserId -> empty`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.PET_NOT_FOUND`
  - `status == 404`

5. `createPet_首只宠物_应自动主宠`
- Mock：
  - `countByUserId -> 0`
  - `objectMapper.writeValueAsString -> "[\"健康正常\"]"`
  - `save -> saved entity`
- 预期：
  - 保存时 `isPrimary == true`
  - `weightKg/avatarUrl` 与请求一致

6. `createPet_非首只宠物_不设主宠`
- Mock：
  - `countByUserId -> 1`
- 预期：
  - 保存时 `isPrimary == false`

7. `createPet_tags序列化失败_应抛校验异常`
- Mock：
  - `objectMapper.writeValueAsString` 抛异常
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.VALIDATION_FAILED`
  - `status == 400`

8. `setPrimary_宠物不存在_应抛异常`
- Mock：
  - `findByIdAndUserId -> empty`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.PET_NOT_FOUND`

9. `setPrimary_成功_应先清空再设新主宠`
- Mock：
  - `findByIdAndUserId -> pet`
- 预期：
  - `clearPrimary(userId)` 调用 1 次
  - 保存宠物 `isPrimary == true`

10. `updateAvatar_宠物不存在_应抛异常`
- Mock：
  - `findByIdAndUserId -> empty`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.PET_NOT_FOUND`

11. `updateAvatar_成功_应更新并保存`
- Mock：
  - `findByIdAndUserId -> pet`
- 预期：
  - `avatarUrl` 被更新
  - `save` 调用 1 次

## 五、UserService
文件：`backend/src/main/java/com/pet/service/UserService.java`

1. `updateAvatar_用户不存在_应抛异常`
- Mock：
  - `findById -> empty`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.USER_NOT_FOUND`
  - `status == 404`

2. `updateAvatar_成功_应更新并保存`
- Mock：
  - `findById -> user`
- 预期：
  - 用户头像更新成功
  - `save` 调用 1 次

## 六、UploadService
文件：`backend/src/main/java/com/pet/service/UploadService.java`

1. `save_空文件_应抛异常`
- Mock：
  - `file == null` 或 `file.isEmpty -> true`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.FILE_REQUIRED`
  - `status == 400`

2. `save_有扩展名_应保留扩展名`
- Mock：
  - `originalFilename -> a.png`
  - `isEmpty -> false`
- 预期：
  - 返回路径后缀为 `.png`

3. `save_无扩展名_应按contentType补扩展名`
- Mock：
  - 文件名无扩展名
  - `contentType -> image/jpeg`
- 预期：
  - 返回路径后缀为 `.jpg`

4. `save_写文件失败_应抛上传异常`
- Mock：
  - `transferTo` 抛 `IOException`
- 预期：
  - 抛出 `BusinessException`
  - `error == ApiError.UPLOAD_FAILED`
  - `status == 500`

5. `save_成功_应返回公开访问路径`
- 输入：固定 baseDir/publicBase/category
- 预期：
  - 返回格式 `/uploads/{category}/{yyyy-MM-dd}/{uuid}.{ext}`

## 七、建议执行顺序
1. AuthService
2. PetService
3. UploadService
4. UserService

## 八、完成标准
- 上述用例全部实现并通过。
- Service 层使用到的每个 `ApiError` 至少有一条失败路径测试覆盖。
- 单元测试不依赖真实 DB / 文件系统 / 网络。
