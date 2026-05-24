<template>
  <div class="chat-bubble" :class="role">
    <div class="avatar" v-if="role === 'user'">👤</div>
    <div class="avatar" v-else>🤖</div>
    <div class="message">
      <div class="content">{{ message }}</div>
      <div class="time">{{ time }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  role: {
    type: String,
    required: true,
    validator: val => ['user', 'assistant'].includes(val)
  },
  message: String,
  time: String
})
</script>

<style scoped>
.chat-bubble {
  display: flex;
  margin-bottom: 18px;
  align-items: flex-end;
}
.chat-bubble.user {
  flex-direction: row-reverse;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  margin: 0 10px;
  box-shadow: var(--shadow-sm);
}
.message {
  max-width: 70%;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 14px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}
.user .message {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border-color: transparent;
}
.content {
  word-break: break-word;
  line-height: 1.45;
  font-size: 14px;
}
.time {
  font-size: 11px;
  color: var(--color-text-muted);
  text-align: right;
  margin-top: 4px;
}
.user .time {
  color: rgba(255,255,255,0.7);
}
</style>