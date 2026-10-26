import { reactive } from 'vue'

export function tablePagination() {
  const pagination = reactive({
    page: 1,
    pageSize: 10,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
    prefix({ itemCount }) {
      return `共 ${itemCount} 条`
    },
    onChange(page) {
      pagination.page = page
    },
    onUpdatePageSize(pageSize) {
      pagination.pageSize = pageSize
      pagination.page = 1
    },
  })
  return pagination
}
