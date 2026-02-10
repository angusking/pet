<template>
  <header class="pet-hero">
    <div class="pet-card">
      <div class="pet-main">
        <a class="avatar" href="#pet-home" aria-label="进入宠物主页">
          <span class="avatar-ring"></span>
          <img class="avatar-img" src="/assets/images/avatar.jpg" alt="宠物头像" />
        </a>
        <div class="pet-meta">
          <div class="pet-title">
            <h1>团子</h1>
            <span class="pet-breed">金毛 · 母 · 已绝育</span>
          </div>
          <div class="pet-sub">2岁3月 · 生日 2023-11-12</div>
          <div class="tag-row">
            <span class="tag">已驱虫</span>
            <span class="tag">已疫苗</span>
            <span class="tag warn">过敏体质</span>
            <span class="tag ok">体重 22.4kg</span>
          </div>
        </div>
        <button class="ghost-btn">编辑宠物资料</button>
      </div>

      <div class="status-card">
        <div class="status-title">今日状态</div>
        <div class="status-line">喂食 2 次 · 喝水 3 次 · 排便 1 次</div>
      </div>

      <div class="album">
        <div class="album-head">
          <div>宠物相册</div>
          <a class="link" href="#album">查看全部</a>
        </div>
        <div class="album-grid">
          <a href="#album"><img src="/assets/images/album-1.jpg" alt="相册1" /></a>
          <a href="#album"><img src="/assets/images/album-2.jpg" alt="相册2" /></a>
          <a href="#album"><img src="/assets/images/album-3.jpg" alt="相册3" /></a>
        </div>
      </div>
    </div>
  </header>
</template>
