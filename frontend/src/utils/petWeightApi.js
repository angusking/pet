import { api } from "./api";

export const petWeightApi = {
  list(petId) {
    return api.get(`/api/pets/${petId}/weights`);
  },
  create(petId, payload) {
    return api.post(`/api/pets/${petId}/weights`, payload);
  },
  remove(petId, recordId) {
    return api.delete(`/api/pets/${petId}/weights/${recordId}`);
  },
};
