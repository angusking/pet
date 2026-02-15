<template>
  <main class="feed">
    <div class="feed-head">
      <div class="feed-title">{{ feedTitle }}</div>
      <div class="feed-actions">
        <button class="pill">下拉刷新</button>
        <button class="pill">上拉加载更多</button>
      </div>
    </div>

    <section class="masonry">
      <article class="card">
        <div class="card-head">
          <span class="user">阿布 · 上海</span>
          <button class="more">不感兴趣 / 屏蔽 / 举报</button>
        </div>
        <div class="media tall">
          <img src="/assets/images/ugc-1.jpg" alt="草地公园" />
          <span class="media-badge">📷 1 / 9</span>
        </div>
        <h3>第一次去草地公园，开心到停不下来</h3>
        <p class="excerpt">今天体验了新的牵引绳，走路更稳，回家直接睡倒。</p>
        <div class="tags">#金毛 #幼犬期 #驱虫</div>
        <div class="meta">2小时前 · 1.2k 浏览</div>
        <div class="actions">
          <button class="icon-btn" aria-label="点赞">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7.5-4.6-9.5-9C1 8 3 5 6 5c2 0 3.5 1.2 4 2.5C10.5 6.2 12 5 14 5c3 0 5 3 3.5 7-2 4.4-9.5 9-9.5 9z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="收藏">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 4h12v16l-6-4-6 4V4z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="评论">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h16v10H8l-4 4V5z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="转发">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 4l5 5-5 5V10H9a5 5 0 0 0-5 5v1" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
        </div>
      </article>

      <article class="card ad">
        <div class="ad-badge">推广 / 广告</div>
        <div class="card-head">
          <span class="user">泡泡宠物洗护 · 4.9分 · 2.3km</span>
        </div>
        <div class="media">
          <img src="/assets/images/ad-grooming.jpg" alt="宠物洗护" />
          <span class="media-badge">🛁 上门洗护</span>
        </div>
        <h3>无泪配方 + 除螨护理，猫狗分区独立</h3>
        <div class="tag-row">
          <span class="tag">上门</span>
          <span class="tag">无泪配方</span>
          <span class="tag">除螨</span>
          <span class="tag">猫狗分区</span>
        </div>
        <div class="ad-detail">套餐：基础洗 / 深度洗 / SPA</div>
        <div class="ad-detail">服务范围：浦东 / 黄浦 · 09:00-21:00</div>
        <button class="primary">一键预约</button>
      </article>

      <article class="card">
        <div class="card-head">
          <span class="user">圆圆 · 杭州</span>
          <button class="more">不感兴趣 / 屏蔽 / 举报</button>
        </div>
        <div class="media">
          <img src="/assets/images/ugc-video-cover.jpg" alt="梳毛视频封面" />
          <span class="media-badge">🎬 00:38</span>
        </div>
        <h3>换季毛量暴增，梳毛的正确打开方式</h3>
        <p class="excerpt">短视频演示：顺毛→逆毛→顺毛，配合喷雾不打结。</p>
        <div class="tags">#梳毛 #皮毛护理</div>
        <div class="meta">昨天 · 980 浏览</div>
        <div class="actions">
          <button class="icon-btn" aria-label="点赞">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7.5-4.6-9.5-9C1 8 3 5 6 5c2 0 3.5 1.2 4 2.5C10.5 6.2 12 5 14 5c3 0 5 3 3.5 7-2 4.4-9.5 9-9.5 9z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="收藏">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 4h12v16l-6-4-6 4V4z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="评论">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h16v10H8l-4 4V5z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="转发">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 4l5 5-5 5V10H9a5 5 0 0 0-5 5v1" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
        </div>
      </article>

      <article class="card ad">
        <div class="ad-badge">推广 / 广告</div>
        <div class="card-head">
          <span class="user">优质宠粮 · 5kg · 高复购</span>
        </div>
        <div class="media">
          <img src="/assets/images/ad-food.jpg" alt="宠物粮" />
          <span class="media-badge">🥣 犬粮主图</span>
        </div>
        <h3>幼犬易消化配方，肠胃友好 / 控体重</h3>
        <div class="tag-row">
          <span class="tag">幼犬</span>
          <span class="tag">肠胃</span>
          <span class="tag">皮毛</span>
          <span class="tag">控体重</span>
        </div>
        <div class="ad-detail">优惠：满减 / 券 / 会员价</div>
        <button class="primary">立即购买</button>
      </article>

      <article class="card">
        <div class="card-head">
          <span class="user">Momo · 北京</span>
          <button class="more">不感兴趣 / 屏蔽 / 举报</button>
        </div>
        <div class="media tall">
          <img src="/assets/images/ugc-2.jpg" alt="饮食调整" />
          <span class="media-badge">📷 1 / 4</span>
        </div>
        <h3>敏感期小贴士：拉稀后如何调整饮食</h3>
        <p class="excerpt">低敏粮 + 少量多餐，配合益生菌观察三天。</p>
        <div class="tags">#拉稀 #肠胃调理</div>
        <div class="meta">3天前 · 3.5k 浏览</div>
        <div class="actions">
          <button class="icon-btn" aria-label="点赞">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7.5-4.6-9.5-9C1 8 3 5 6 5c2 0 3.5 1.2 4 2.5C10.5 6.2 12 5 14 5c3 0 5 3 3.5 7-2 4.4-9.5 9-9.5 9z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="收藏">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 4h12v16l-6-4-6 4V4z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="评论">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h16v10H8l-4 4V5z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="转发">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 4l5 5-5 5V10H9a5 5 0 0 0-5 5v1" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
        </div>
      </article>

      <article class="card ad">
        <div class="ad-badge">推广 / 广告</div>
        <div class="card-head">
          <span class="user">周末宠物旅行 · 名额 12</span>
        </div>
        <div class="media">
          <img src="/assets/images/ad-travel.jpg" alt="宠物旅行" />
          <span class="media-badge">🏕️ 海边露营</span>
        </div>
        <h3>宠物友好酒店 + 专属草坪 + 摄影师跟拍</h3>
        <div class="ad-detail">时间：本周六 09:30 · 集合：南站北广场</div>
        <div class="ad-detail">注意事项：需疫苗证明 / 中大型犬需口罩</div>
        <button class="primary">立即报名</button>
      </article>

      <article class="card">
        <div class="card-head">
          <span class="user">米线 · 广州</span>
          <button class="more">不感兴趣 / 屏蔽 / 举报</button>
        </div>
        <div class="media">
          <img src="/assets/images/ugc-3.jpg" alt="雨天遛狗" />
          <span class="media-badge">📷 1 / 2</span>
        </div>
        <h3>雨天遛狗装备清单</h3>
        <p class="excerpt">防水背带、鞋套、吸水巾，再加一个反光牌。</p>
        <div class="tags">#雨天遛狗 #装备</div>
        <div class="meta">刚刚 · 210 浏览</div>
        <div class="actions">
          <button class="icon-btn" aria-label="点赞">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7.5-4.6-9.5-9C1 8 3 5 6 5c2 0 3.5 1.2 4 2.5C10.5 6.2 12 5 14 5c3 0 5 3 3.5 7-2 4.4-9.5 9-9.5 9z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="收藏">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 4h12v16l-6-4-6 4V4z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="评论">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h16v10H8l-4 4V5z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="转发">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 4l5 5-5 5V10H9a5 5 0 0 0-5 5v1" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
        </div>
      </article>

      <article class="card merchant">
        <div class="merchant-badge">商家认证</div>
        <div class="card-head">
          <span class="user">宠物诊所 · 深圳</span>
          <button class="more">不感兴趣 / 屏蔽 / 举报</button>
        </div>
        <div class="media">
          <img src="/assets/images/merchant-clinic.jpg" alt="宠物诊所" />
          <span class="media-badge">🏥 免费健康咨询</span>
        </div>
        <h3>春季过敏高发，在线问诊专场</h3>
        <p class="excerpt">图文问诊 + 线下检查优惠券，限本周。</p>
        <div class="tags">#健康咨询 #过敏体质</div>
        <div class="meta">1天前 · 2.1k 浏览</div>
        <div class="actions">
          <button class="icon-btn" aria-label="点赞">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7.5-4.6-9.5-9C1 8 3 5 6 5c2 0 3.5 1.2 4 2.5C10.5 6.2 12 5 14 5c3 0 5 3 3.5 7-2 4.4-9.5 9-9.5 9z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="收藏">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 4h12v16l-6-4-6 4V4z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="评论">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h16v10H8l-4 4V5z" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
          <button class="icon-btn" aria-label="转发">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 4l5 5-5 5V10H9a5 5 0 0 0-5 5v1" fill="none" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  feedType: {
    type: String,
    default: "discover",
  },
});

const feedTitle = computed(() => (props.feedType === "follow" ? "关注动态" : "发现动态"));
</script>

