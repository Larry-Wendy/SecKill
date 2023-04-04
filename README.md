# SecKill
A commodity sec-kill system is designed based on SpringBoot

- Build project framework:
  - Build SpringBoot environment
  - Integrate Thymeleaf, RespBean
  - Use MyBatis to interact with MySQL database
- Distributed session
  - User login
    - Design database table
    - Encrypt plaintext password with MD5 twice
    - Parameter verification + global exception handling
  - Shared session
    - SpringSession
    - Redis
- Develop main functionalities
  - Goods List
  - Goods Detail
  - SecKill
  - Order Detail
- Measure system pressure
  - JMeter
  - Simulate multiple users through custom variables
  - Measure: Goods List + SecKill
- Page optimization
  - Page cache + URL cache + object cache
  - Front and back end separation
  - Static Asset Optimization
- Interface optimization
  - Reduce database access through redis pre-inventory
  - Reduce redis access by using memory tag
  - Place orders asynchronously by RabbitMQ
- Security optimization
  - Hide seckill interface
  - Verification code
  - Interface throttling
  

<img src="https://github.com/Larry-Wendy/SecKill/blob/main/gif/1-min.gif" width="700" height="520"/>

The techniques used in the project are shown below:  
<img src="https://github.com/Larry-Wendy/SecKill/blob/main/gif/tech.png" width="700" height="410"/>
