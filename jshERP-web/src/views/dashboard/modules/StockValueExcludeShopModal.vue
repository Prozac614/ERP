<template>
  <a-modal
    title="排除店铺后的库存总金额"
    :width="800"
    :visible="visible"
    :confirmLoading="loading"
    :maskClosable="false"
    @cancel="handleCancel"
    cancelText="关闭"
    :footer="null"
    style="top:20%;height: auto;">
    <a-spin :spinning="loading">
      <a-form layout="vertical">
        <a-form-item label="选择日期范围" :required="true">
          <a-range-picker
            v-model="queryDateRange"
            format="YYYY-MM-DD"
            :placeholder="['开始日期', '结束日期']"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="排除店铺" :required="true">
          <a-select
            v-model="selectedShops"
            mode="multiple"
            placeholder="请选择要排除的店铺"
            style="width: 100%"
            allowClear
            :open="shopSelectOpen"
            @change="handleShopSelectChange"
            @select="handleShopSelect"
            @dropdownVisibleChange="handleDropdownVisibleChange"
          >
            <a-select-option value="__ALL__" :disabled="selectedShops.length > 0">
              排除所有店铺
            </a-select-option>
            <a-select-option v-for="shop in shopList" :key="shop.id" :value="shop.name">
              {{ shop.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleQuery" :loading="loading" block>
            查询
          </a-button>
        </a-form-item>
        <a-form-item v-if="tableData && tableData.length > 0">
          <a-table
            :columns="columns"
            :dataSource="paginatedData"
            :pagination="pagination"
            @change="handleTableChange"
            size="small"
            bordered
            rowKey="date"
          >
            <template slot="excludeAfterValue" slot-scope="text">
              <span style="font-weight: bold; color: #1890ff;">
                {{ formatCurrency(text) }}
              </span>
            </template>
            <template slot="excludeBeforeValue" slot-scope="text">
              <span style="font-weight: bold; color: #52c41a;">
                {{ formatCurrency(text) }}
              </span>
            </template>
          </a-table>
        </a-form-item>
      </a-form>
    </a-spin>
  </a-modal>
</template>

<script>
import moment from 'moment'
import { getAction } from '@/api/manage'

export default {
  name: "StockValueExcludeShopModal",
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    shopList: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      loading: false,
      queryDateRange: [moment(), moment()],
      selectedShops: [],
      shopSelectOpen: false,
      tableData: [],
      columns: [
        {
          title: '日期',
          dataIndex: 'date',
          width: 120,
          align: 'center'
        },
        {
          title: '排除后当日总金额',
          dataIndex: 'excludeAfterValue',
          width: 180,
          align: 'right',
          scopedSlots: { customRender: 'excludeAfterValue' }
        },
        {
          title: '排除前当日总金额',
          dataIndex: 'excludeBeforeValue',
          width: 180,
          align: 'right',
          scopedSlots: { customRender: 'excludeBeforeValue' }
        }
      ],
      pagination: {
        current: 1,
        pageSize: 10,
        pageSizeOptions: ['10', '20', '50', '100'],
        showTotal: (total) => `共 ${total} 条`,
        showSizeChanger: true,
        showQuickJumper: true,
        total: 0
      }
    }
  },
  computed: {
    paginatedData() {
      const { current, pageSize } = this.pagination
      const start = (current - 1) * pageSize
      const end = start + pageSize
      return this.tableData.slice(start, end)
    },
    allShopNames() {
      return this.shopList.map(shop => shop.name)
    }
  },
  watch: {
    visible(newVal) {
      if (newVal) {
        // 弹窗打开时重置数据
        this.queryDateRange = [moment(), moment()]
        this.selectedShops = []
        this.shopSelectOpen = false
        this.tableData = []
        this.pagination.current = 1
        this.pagination.total = 0
      }
    }
  },
  methods: {
    handleCancel() {
      this.$emit('cancel')
    },
    validateDateRange() {
      if (!this.queryDateRange || this.queryDateRange.length !== 2) {
        this.$message.warning('请选择日期范围')
        return false
      }
      const [beginDate, endDate] = this.queryDateRange
      if (!beginDate || !endDate) {
        this.$message.warning('请选择完整的日期范围')
        return false
      }
      const daysDiff = endDate.diff(beginDate, 'days')
      if (daysDiff < 0) {
        this.$message.warning('开始日期不能大于结束日期')
        return false
      }
      if (endDate.isAfter(moment(), 'day')) {
        this.$message.warning('结束日期不能是未来日期')
        return false
      }
      return true
    },
    async handleQuery() {
      if (!this.validateDateRange()) {
        return
      }
      if (!this.selectedShops || this.selectedShops.length === 0) {
        this.$message.warning('请选择要排除的店铺')
        return
      }
      
      this.loading = true
      this.tableData = []
      
      try {
        const beginDate = this.queryDateRange[0].format('YYYY-MM-DD')
        const endDate = this.queryDateRange[1].format('YYYY-MM-DD')
        const excludeShopNames = this.selectedShops.length > 0 ? this.selectedShops.join(',') : ''
        const res = await getAction('/depotItem/getTotalStockValueExcludeShopByDateRange', {
          beginDate: beginDate,
          endDate: endDate,
          excludeShopNames: excludeShopNames
        })
        
        if (res && res.code === 200 && res.data) {
          this.tableData = res.data || []
          this.pagination.total = this.tableData.length
          this.pagination.current = 1
          if (this.tableData.length === 0) {
            this.$message.info('未查询到数据')
          }
        } else {
          this.$message.error(res.data || '查询失败')
          this.tableData = []
          this.pagination.total = 0
        }
      } catch (e) {
        console.error('查询失败:', e)
        this.$message.error('查询失败，请重试')
        this.tableData = []
      } finally {
        this.loading = false
      }
    },
    formatCurrency(val) {
      const num = Number(val)
      if (!isFinite(num)) {
        return '-'
      }
      return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },
    handleTableChange(pagination) {
      this.pagination.current = pagination.current
      this.pagination.pageSize = pagination.pageSize
    },
    handleDropdownVisibleChange(open) {
      // 同步下拉框的显示状态
      this.shopSelectOpen = open
    },
    handleShopSelect(value) {
      // 选择选项后自动收起下拉框
      this.shopSelectOpen = false
    },
    handleShopSelectChange(value) {
      if (value && value.includes('__ALL__')) {
        // 选择"排除所有店铺"时，自动选择所有店铺
        this.selectedShops = this.allShopNames.filter(name => name !== '__ALL__')
        // 收起下拉框
        this.shopSelectOpen = false
      }
    }
  }
}
</script>

<style scoped>
@import '~@assets/less/common.less'
</style>

