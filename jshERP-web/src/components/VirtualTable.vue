<template>
  <div class="virtual-table-container" ref="container" @scroll="handleScroll">
    <!-- 表头 -->
    <div class="virtual-table-header" :style="{ transform: `translateX(-${scrollLeft}px)` }">
      <a-table
        :columns="columns"
        :dataSource="[]"
        :pagination="false"
        :scroll="{ x: tableWidth }"
        size="middle"
        class="header-table"
      />
    </div>
    
    <!-- 虚拟滚动内容区域 -->
    <div class="virtual-table-body" :style="{ height: containerHeight + 'px' }">
      <!-- 占位元素，用于撑起总高度 -->
      <div :style="{ height: totalHeight + 'px', position: 'relative' }">
        <!-- 可见区域的表格 -->
        <div 
          class="visible-rows"
          :style="{ 
            transform: `translateY(${offsetY}px)`,
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0
          }"
        >
          <a-table
            :columns="columns"
            :dataSource="visibleData"
            :pagination="false"
            :scroll="{ x: tableWidth }"
            :rowKey="rowKey"
            size="middle"
            :showHeader="false"
            @row="handleRowEvents"
          />
        </div>
      </div>
    </div>
    
    <!-- 性能监控显示 -->
    <div class="performance-info" v-if="showPerformanceInfo">
      <span>渲染行数: {{ visibleData.length }} / {{ dataSource.length }}</span>
      <span>渲染率: {{ renderRatio }}%</span>
      <span>滚动位置: {{ scrollTop }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'VirtualTable',
  props: {
    dataSource: {
      type: Array,
      default: () => []
    },
    columns: {
      type: Array,
      default: () => []
    },
    rowHeight: {
      type: Number,
      default: 54 // 默认行高
    },
    containerHeight: {
      type: Number,
      default: 400 // 容器高度
    },
    rowKey: {
      type: [String, Function],
      default: 'key'
    },
    overscan: {
      type: Number,
      default: 5 // 预渲染额外行数，提升滚动体验
    },
    showPerformanceInfo: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      scrollTop: 0,
      scrollLeft: 0,
      visibleData: [],
      startIndex: 0,
      endIndex: 0,
      offsetY: 0
    }
  },
  computed: {
    // 总高度
    totalHeight() {
      return this.dataSource.length * this.rowHeight
    },
    
    // 可见行数
    visibleCount() {
      return Math.ceil(this.containerHeight / this.rowHeight)
    },
    
    // 表格总宽度
    tableWidth() {
      return this.columns.reduce((width, col) => {
        return width + (col.width || 150)
      }, 0)
    },
    
    // 渲染率
    renderRatio() {
      if (this.dataSource.length === 0) return 100
      return Math.round((this.visibleData.length / this.dataSource.length) * 100)
    }
  },
  watch: {
    dataSource: {
      handler() {
        this.updateVisibleData()
      },
      immediate: true
    }
  },
  mounted() {
    this.updateVisibleData()
  },
  methods: {
    // 处理滚动事件
    handleScroll(event) {
      const { scrollTop, scrollLeft } = event.target
      this.scrollTop = scrollTop
      this.scrollLeft = scrollLeft
      
      // 防抖优化
      if (this.scrollTimer) {
        clearTimeout(this.scrollTimer)
      }
      
      this.scrollTimer = setTimeout(() => {
        this.updateVisibleData()
      }, 16) // 60fps
    },
    
    // 更新可见数据
    updateVisibleData() {
      if (this.dataSource.length === 0) {
        this.visibleData = []
        return
      }
      
      // 计算可见范围
      const startIndex = Math.floor(this.scrollTop / this.rowHeight)
      const endIndex = Math.min(
        startIndex + this.visibleCount + this.overscan * 2,
        this.dataSource.length
      )
      
      // 加上overscan，提前渲染一些行
      const actualStartIndex = Math.max(0, startIndex - this.overscan)
      const actualEndIndex = Math.min(this.dataSource.length, endIndex + this.overscan)
      
      this.startIndex = actualStartIndex
      this.endIndex = actualEndIndex
      
      // 提取可见数据
      this.visibleData = this.dataSource.slice(actualStartIndex, actualEndIndex)
      
      // 计算偏移量
      this.offsetY = actualStartIndex * this.rowHeight
      
      // 触发事件
      this.$emit('visible-change', {
        startIndex: actualStartIndex,
        endIndex: actualEndIndex,
        visibleData: this.visibleData
      })
    },
    
    // 处理行事件
    handleRowEvents(record, index) {
      const actualIndex = this.startIndex + index
      return {
        on: {
          click: () => {
            this.$emit('row-click', record, actualIndex)
          },
          dblclick: () => {
            this.$emit('row-dblclick', record, actualIndex)
          }
        }
      }
    },
    
    // 滚动到指定位置
    scrollToIndex(index) {
      const scrollTop = index * this.rowHeight
      this.$refs.container.scrollTop = scrollTop
    },
    
    // 滚动到顶部
    scrollToTop() {
      this.scrollToIndex(0)
    },
    
    // 滚动到底部
    scrollToBottom() {
      this.scrollToIndex(this.dataSource.length - 1)
    }
  },
  beforeDestroy() {
    if (this.scrollTimer) {
      clearTimeout(this.scrollTimer)
    }
  }
}
</script>

<style scoped>
.virtual-table-container {
  position: relative;
  overflow: auto;
  border: 1px solid #f0f0f0;
}

.virtual-table-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.virtual-table-body {
  overflow: hidden;
}

.header-table {
  margin-bottom: 0;
}

.header-table >>> .ant-table-tbody {
  display: none;
}

.visible-rows >>> .ant-table-thead {
  display: none;
}

.performance-info {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 5px 10px;
  border-radius: 4px;
  font-size: 12px;
  z-index: 20;
}

.performance-info span {
  margin-right: 10px;
}

/* 优化滚动条 */
.virtual-table-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.virtual-table-container::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.virtual-table-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.virtual-table-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style> 