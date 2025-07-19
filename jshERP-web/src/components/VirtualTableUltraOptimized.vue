<template>
  <div class="virtual-table-ultra" ref="container" @scroll="onScroll">
    <!-- 表头 -->
    <div class="table-header" :style="headerStyle">
      <div class="header-content" :style="{ transform: `translateX(-${scrollLeft}px)` }">
        <!-- 固定列表头 -->
        <div class="fixed-header">
          <div 
            v-for="(col, index) in fixedColumns" 
            :key="`header-${index}`"
            class="header-cell"
            :style="{ width: columnWidth + 'px' }"
          >
            {{ col.title }}
          </div>
        </div>
        <!-- 滚动列表头 -->
        <div class="scroll-header" :style="{ transform: `translateX(${virtualLeft}px)` }">
          <div 
            v-for="(col, index) in visibleScrollColumns" 
            :key="`scroll-header-${col.realIndex}`"
            class="header-cell"
            :style="{ width: columnWidth + 'px' }"
          >
            {{ col.title }}
          </div>
        </div>
      </div>
    </div>

    <!-- 数据区域 -->
    <div class="table-body" :style="{ height: containerHeight + 'px' }">
      <div class="table-phantom" :style="{ height: totalHeight + 'px' }">
        <div 
          class="visible-area"
          :style="{ transform: `translateY(${virtualTop}px)` }"
        >
          <!-- 只渲染可见行 -->
          <div 
            v-for="(rowIndex, index) in visibleRowIndexes"
            :key="`row-${rowIndex}`"
            class="table-row"
            :style="{ height: rowHeight + 'px', transform: `translateX(-${scrollLeft}px)` }"
          >
            <!-- 固定列数据 -->
            <div class="fixed-columns">
              <div 
                v-for="(col, colIndex) in fixedColumns"
                :key="`cell-${rowIndex}-${colIndex}`"
                class="table-cell"
                :style="{ width: columnWidth + 'px' }"
              >
                {{ getCellData(rowIndex, col.dataIndex) }}
              </div>
            </div>
            
            <!-- 滚动列数据 -->
            <div class="scroll-columns" :style="{ transform: `translateX(${virtualLeft}px)` }">
              <div 
                v-for="(col, colIndex) in visibleScrollColumns"
                :key="`scroll-cell-${rowIndex}-${col.realIndex}`"
                class="table-cell"
                :style="{ width: columnWidth + 'px' }"
              >
                {{ getCellData(rowIndex, col.dataIndex) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 内存监控 -->
    <div v-if="showMemoryInfo" class="memory-info">
      <div>渲染单元格: {{ renderedCells }}</div>
      <div>数据行: {{ visibleRowIndexes.length }}/{{ totalRows }}</div>
      <div>数据列: {{ visibleScrollColumns.length }}/{{ scrollColumns.length }}</div>
      <div>内存节省: {{ memorySavingPercent }}%</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'VirtualTableUltraOptimized',
  props: {
    // 数据源 - 改为函数式获取，减少内存占用
    dataGetter: {
      type: Function,
      required: true
    },
    totalRows: {
      type: Number,
      required: true
    },
    columns: {
      type: Array,
      required: true
    },
    containerHeight: {
      type: Number,
      default: 600
    },
    rowHeight: {
      type: Number,
      default: 40
    },
    columnWidth: {
      type: Number,
      default: 100
    },
    fixedColumnCount: {
      type: Number,
      default: 3
    },
    bufferSize: {
      type: Number,
      default: 3
    },
    showMemoryInfo: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      scrollTop: 0,
      scrollLeft: 0,
      containerWidth: 800,
      scrollTimer: null,
      cellDataCache: new Map(), // 单元格数据缓存
      lastVisibleRange: { startRow: -1, endRow: -1, startCol: -1, endCol: -1 }
    }
  },
  computed: {
    // 总高度
    totalHeight() {
      return this.totalRows * this.rowHeight
    },

    // 可见行数
    visibleRowCount() {
      return Math.ceil(this.containerHeight / this.rowHeight) + this.bufferSize * 2
    },

    // 可见列数
    visibleColumnCount() {
      const scrollWidth = this.containerWidth - this.fixedColumnCount * this.columnWidth
      return Math.ceil(scrollWidth / this.columnWidth) + this.bufferSize * 2
    },

    // 可见行范围
    visibleRowRange() {
      const startRow = Math.max(0, Math.floor(this.scrollTop / this.rowHeight) - this.bufferSize)
      const endRow = Math.min(this.totalRows - 1, startRow + this.visibleRowCount)
      return { startRow, endRow }
    },

    // 可见列范围  
    visibleColumnRange() {
      const scrollableWidth = this.containerWidth - this.fixedColumnCount * this.columnWidth
      const scrollOffset = Math.max(0, this.scrollLeft - this.fixedColumnCount * this.columnWidth)
      
      const startCol = Math.max(0, Math.floor(scrollOffset / this.columnWidth) - this.bufferSize)
      const endCol = Math.min(this.scrollColumns.length - 1, startCol + this.visibleColumnCount)
      
      return { startCol, endCol }
    },

    // 固定列
    fixedColumns() {
      return this.columns.slice(0, this.fixedColumnCount)
    },

    // 滚动列
    scrollColumns() {
      return this.columns.slice(this.fixedColumnCount)
    },

    // 可见行索引
    visibleRowIndexes() {
      const { startRow, endRow } = this.visibleRowRange
      const indexes = []
      for (let i = startRow; i <= endRow; i++) {
        indexes.push(i)
      }
      return indexes
    },

    // 可见滚动列
    visibleScrollColumns() {
      const { startCol, endCol } = this.visibleColumnRange
      return this.scrollColumns.slice(startCol, endCol + 1).map((col, index) => ({
        ...col,
        realIndex: startCol + index
      }))
    },

    // 虚拟偏移
    virtualTop() {
      return this.visibleRowRange.startRow * this.rowHeight
    },

    virtualLeft() {
      return this.visibleColumnRange.startCol * this.columnWidth
    },

    // 表头样式
    headerStyle() {
      return {
        height: this.rowHeight + 'px',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        background: '#fafafa',
        borderBottom: '2px solid #f0f0f0'
      }
    },

    // 渲染的单元格数量
    renderedCells() {
      return this.visibleRowIndexes.length * (this.fixedColumns.length + this.visibleScrollColumns.length)
    },

    // 内存节省百分比
    memorySavingPercent() {
      const totalCells = this.totalRows * this.columns.length
      const savedCells = totalCells - this.renderedCells
      return totalCells > 0 ? Math.round((savedCells / totalCells) * 100) : 0
    }
  },
  mounted() {
    this.updateContainerWidth()
    window.addEventListener('resize', this.updateContainerWidth)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateContainerWidth)
    this.clearCache()
  },
  methods: {
    // 滚动处理（高度优化）
    onScroll(event) {
      const { scrollTop, scrollLeft } = event.target
      
      // 防抖处理
      if (this.scrollTimer) {
        cancelAnimationFrame(this.scrollTimer)
      }
      
      this.scrollTimer = requestAnimationFrame(() => {
        this.scrollTop = scrollTop
        this.scrollLeft = scrollLeft
        this.cleanupCache()
      })
    },

    // 获取单元格数据（超轻量级）
    getCellData(rowIndex, columnKey) {
      const cacheKey = `${rowIndex}-${columnKey}`
      
      // 检查缓存
      if (this.cellDataCache.has(cacheKey)) {
        return this.cellDataCache.get(cacheKey)
      }
      
      // 通过函数获取数据
      const value = this.dataGetter(rowIndex, columnKey)
      
      // 缓存结果（但限制缓存大小）
      if (this.cellDataCache.size < 10000) {
        this.cellDataCache.set(cacheKey, value)
      }
      
      return value
    },

    // 更新容器宽度
    updateContainerWidth() {
      if (this.$refs.container) {
        this.containerWidth = this.$refs.container.clientWidth
      }
    },

    // 清理缓存
    cleanupCache() {
      // 只保留可见区域附近的缓存
      const { startRow, endRow } = this.visibleRowRange
      const { startCol, endCol } = this.visibleColumnRange
      
      const bufferRows = 20
      const bufferCols = 10
      
      const keepRowStart = Math.max(0, startRow - bufferRows)
      const keepRowEnd = Math.min(this.totalRows - 1, endRow + bufferRows)
      
      // 清理过期缓存
      for (const [key] of this.cellDataCache) {
        const [rowStr] = key.split('-')
        const row = parseInt(rowStr)
        
        if (row < keepRowStart || row > keepRowEnd) {
          this.cellDataCache.delete(key)
        }
      }
      
      // 限制缓存大小
      if (this.cellDataCache.size > 5000) {
        const keysToDelete = Array.from(this.cellDataCache.keys()).slice(0, 2000)
        keysToDelete.forEach(key => this.cellDataCache.delete(key))
      }
    },

    // 清空所有缓存
    clearCache() {
      this.cellDataCache.clear()
    },

    // 滚动到指定位置
    scrollToRow(rowIndex) {
      if (this.$refs.container) {
        this.$refs.container.scrollTop = rowIndex * this.rowHeight
      }
    },

    // 刷新数据
    refresh() {
      this.clearCache()
      this.$forceUpdate()
    }
  },
  watch: {
    totalRows() {
      this.clearCache()
    },
    columns() {
      this.clearCache()
    }
  }
}
</script>

<style scoped>
.virtual-table-ultra {
  position: relative;
  overflow: auto;
  border: 1px solid #f0f0f0;
  background: white;
  contain: layout style paint;
}

.table-header {
  overflow: hidden;
}

.header-content {
  display: flex;
  align-items: center;
}

.fixed-header, .scroll-header {
  display: flex;
}

.fixed-header {
  position: sticky;
  left: 0;
  z-index: 10;
  background: #fafafa;
  border-right: 2px solid #f0f0f0;
}

.header-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
  border-right: 1px solid #f0f0f0;
  font-size: 12px;
  font-weight: 600;
  background: #fafafa;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-body {
  overflow: hidden;
  position: relative;
}

.table-phantom {
  position: relative;
}

.visible-area {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
}

.table-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #f5f5f5;
}

.fixed-columns, .scroll-columns {
  display: flex;
}

.fixed-columns {
  position: sticky;
  left: 0;
  z-index: 5;
  background: white;
  border-right: 2px solid #f0f0f0;
}

.table-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-right: 1px solid #f5f5f5;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: white;
}

.table-row:hover .table-cell {
  background: #f5f5f5;
}

.memory-info {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 8px;
  border-radius: 4px;
  font-size: 11px;
  z-index: 200;
  line-height: 1.3;
}

/* 滚动条优化 */
.virtual-table-ultra::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.virtual-table-ultra::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.virtual-table-ultra::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.virtual-table-ultra::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style> 